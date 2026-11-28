package net.rose.customPlugin.listener.disc;

import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.api.AbilityListener;
import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.effect.HatEffects;
import net.rose.customPlugin.effect.ZeusDiscFlight;
import net.rose.customPlugin.item.misc.Hat;
import net.rose.customPlugin.util.DeathMessages;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class HatListener extends AbilityListener<Hat> {

    private static final int OFFHAND_SLOT = 40;
    /** How fast the thrown hat flies compared to the Zeus Disc (1.0). Lower is slower. */
    private static final double FLIGHT_SPEED_SCALE = 0.6;

    private static final long PAD_COOLDOWN_MS = 20_000;
    private static final long RAZOR_COOLDOWN_MS = 25_000;
    private static final long RAZOR_MISS_COOLDOWN_MS = 8_000; // shorter when the razor hat hits nobody

    private static final String PAD_ID = "hat_bounce";
    private static final String RAZOR_ID = "hat_razor";

    private final HatEffects effects;
    private final DeathMessages deaths;
    private final Map<UUID, ZeusDiscFlight> flights = new HashMap<>();

    public HatListener(WeaponContext context, Hat hat, HatEffects effects) {
        super(context, hat);
        this.effects = effects;
        this.deaths = context.deaths();
    }

    /** Offhand key (F) while holding the Hat: throw the bounce hat, or the razor hat when crouching. The swap is always cancelled. */
    @Override
    protected void onOffhand(Player player, boolean sneaking) {
        throwHat(player);
    }

    /** Right-click throws the Hat like a disc: it damages the first thing it hits and flies back to the thrower. */
    // Not ignoreCancelled: a click on air has no block to use, so Bukkit reports it as cancelled from the start.
    // What matters is whether another plugin denied using the item.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.useItemInHand() == Event.Result.DENY) return;

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return;
        if (!weapon.isWeapon(event.getItem())) return;

        Player player = event.getPlayer();
        // Chests, doors, buttons... still work like normal, unless the player is sneaking.
        if (action == Action.RIGHT_CLICK_BLOCK
                && !player.isSneaking()
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType().isInteractable()) {
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        throwDisc(player, hand);
    }

    private void throwHat(Player player) {
        if (effects.hasThrown(player)) return; // only one hat out at a time
        if (player.isSneaking()) {
            throwRazor(player);
        } else {
            throwPad(player);
        }
    }

    /** Takes the hat out of the player's hand and throws it; it is handed back when the flight ends. */
    private void throwDisc(Player player, EquipmentSlot hand) {
        UUID id = player.getUniqueId();
        if (flights.containsKey(id) || effects.hasThrown(player)) return; // one hat out at a time

        PlayerInventory inventory = player.getInventory();
        int slot = hand == EquipmentSlot.OFF_HAND ? OFFHAND_SLOT : inventory.getHeldItemSlot();
        ItemStack held = inventory.getItem(slot);
        if (!weapon.isWeapon(held)) return;

        ItemStack thrown = held.clone();
        thrown.setAmount(1);
        inventory.setItem(slot, null);
        try {
            flights.put(id, ZeusDiscFlight.launch(plugin, null, deaths, flight -> flights.remove(id, flight),
                    player, thrown, slot, false, DeathMessages.HAT, true, FLIGHT_SPEED_SCALE));
        } catch (Throwable t) {
            // The throw failed after the hat left the inventory: put it straight back.
            plugin.getLogger().log(Level.SEVERE, "Could not throw the Hat", t);
            inventory.setItem(slot, thrown);
        }
    }

    /** Ends every flight (each hat goes back to its thrower); call from onDisable. */
    public void shutdown() {
        for (ZeusDiscFlight flight : new ArrayList<>(flights.values())) {
            flight.end();
        }
    }

    private void throwPad(Player player) {
        if (cooldowns.isOnCooldown(player, PAD_ID)) return; // boss bar already shows the time left
        TakenHat taken = takeFromHand(player);
        if (taken == null) return;
        try {
            effects.throwPad(player, taken.item(), taken.slot());
        } catch (Throwable t) {
            restore(player, taken, t);
            return;
        }
        cooldowns.start(player, PAD_ID, "Bounce Hat", BossBar.Color.GREEN, PAD_COOLDOWN_MS);
    }

    private void throwRazor(Player player) {
        if (cooldowns.isOnCooldown(player, RAZOR_ID)) return;
        TakenHat taken = takeFromHand(player);
        if (taken == null) return;
        try {
            effects.throwRazor(player, taken.item(), taken.slot(), () ->
                    cooldowns.start(player, RAZOR_ID, "Razor Hat", BossBar.Color.RED, RAZOR_MISS_COOLDOWN_MS));
        } catch (Throwable t) {
            restore(player, taken, t);
            return;
        }
        cooldowns.start(player, RAZOR_ID, "Razor Hat", BossBar.Color.RED, RAZOR_COOLDOWN_MS);
    }

    /** The hat that was taken out of a hand for a throw, and the slot it came from. */
    private record TakenHat(ItemStack item, int slot) {
    }

    /** Takes the hat out of the main hand, like the Zeus Disc does when thrown. Null if the hand holds no hat. */
    private TakenHat takeFromHand(Player player) {
        PlayerInventory inventory = player.getInventory();
        int slot = inventory.getHeldItemSlot();
        ItemStack held = inventory.getItem(slot);
        if (!weapon.isWeapon(held)) return null;

        ItemStack thrown = held.clone();
        thrown.setAmount(1);
        inventory.setItem(slot, null);
        return new TakenHat(thrown, slot);
    }

    /** The throw failed after the hat left the inventory: put it straight back. */
    private void restore(Player player, TakenHat taken, Throwable cause) {
        plugin.getLogger().log(Level.SEVERE, "Could not throw the Hat", cause);
        player.getInventory().setItem(taken.slot(), taken.item());
    }

    /** Whoever the bounce hat launched takes no fall damage when they come back down. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (event.getEntity() instanceof LivingEntity living && effects.consumeFallImmunity(living)) {
            event.setCancelled(true);
        }
    }

    @Override
    protected void onPlayerDeath(Player player) {
        effects.cancelAndDrop(player); // the hat is not in the inventory, so it drops where it is
    }

    @Override
    protected void onPlayerQuit(Player player) {
        ZeusDiscFlight flight = flights.get(player.getUniqueId());
        if (flight != null) flight.end(); // a hat in the air would be lost if the player left
        effects.cancel(player);
    }
}
