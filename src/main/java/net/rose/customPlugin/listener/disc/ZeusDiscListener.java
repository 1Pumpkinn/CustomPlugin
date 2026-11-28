package net.rose.customPlugin.listener.disc;

import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.api.AbilityListener;
import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.effect.ZeusDiscFlight;
import net.rose.customPlugin.effect.ZeusLightning;
import net.rose.customPlugin.item.disc.ZeusDisc;
import net.rose.customPlugin.util.DeathMessages;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class ZeusDiscListener extends AbilityListener<ZeusDisc> {

    private static final long STRIKE_COOLDOWN_MS = 20_000;
    private static final String STRIKE_ID = "zeus_strike";
    private static final int OFFHAND_SLOT = 40;

    private final ZeusLightning lightning;
    private final DeathMessages deaths;

    private final Set<UUID> armed = new HashSet<>();
    private final Map<UUID, ZeusDiscFlight> flights = new HashMap<>();

    public ZeusDiscListener(WeaponContext context, ZeusDisc disc, ZeusLightning lightning) {
        super(context, disc);
        this.lightning = lightning;
        this.deaths = context.deaths();
    }

    /** Offhand key (F) while holding the Zeus Disc primes the next throw. The swap itself is always cancelled. */
    @Override
    protected void onOffhand(Player player, boolean sneaking) {
        prime(player);
    }

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

    /** Dropping the weapon cancels a primed ability, so its boss bar doesn't stay behind. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!weapon.isWeapon(event.getItemDrop().getItemStack())) return;
        Player player = event.getPlayer();
        if (armed.remove(player.getUniqueId())) cooldowns.unprime(player, STRIKE_ID);
    }

    @Override
    protected void onPlayerDeath(Player player) {
        armed.remove(player.getUniqueId());
        cooldowns.unprime(player, STRIKE_ID);
    }

    /** A disc in the air would be lost if the player left, so it comes home first. */
    @Override
    protected void onPlayerQuit(Player player) {
        armed.remove(player.getUniqueId());
        ZeusDiscFlight flight = flights.get(player.getUniqueId());
        if (flight != null) flight.end();
    }

    /** Ends every flight (each disc goes back to its thrower); call from onDisable. */
    public void shutdown() {
        for (ZeusDiscFlight flight : new ArrayList<>(flights.values())) {
            flight.end();
        }
    }

    private void prime(Player player) {
        if (cooldowns.isOnCooldown(player, STRIKE_ID)) return; // boss bar already shows the time left
        if (!armed.add(player.getUniqueId())) return;
        cooldowns.prime(player, STRIKE_ID, "Zeus Strike", BossBar.Color.YELLOW);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
    }

    /** Takes the disc out of the player's hand and throws it. */
    private void throwDisc(Player player, EquipmentSlot hand) {
        UUID id = player.getUniqueId();
        if (flights.containsKey(id)) return; // one disc, one flight

        PlayerInventory inventory = player.getInventory();
        int slot = hand == EquipmentSlot.OFF_HAND ? OFFHAND_SLOT : inventory.getHeldItemSlot();
        ItemStack held = inventory.getItem(slot);
        if (!weapon.isWeapon(held)) return;

        ItemStack thrown = held.clone();
        thrown.setAmount(1);

        boolean primed = armed.remove(id);
        if (primed) cooldowns.unprime(player, STRIKE_ID);
        inventory.setItem(slot, null);
        try {
            flights.put(id, ZeusDiscFlight.launch(plugin, lightning, deaths, flight -> flights.remove(id, flight),
                    player, thrown, slot, primed, DeathMessages.ZEUS_DISC, false, 1.0));
        } catch (Throwable t) {
            // The throw failed after the disc left the inventory: put it straight back.
            plugin.getLogger().log(Level.SEVERE, "Could not throw the Zeus Disc", t);
            inventory.setItem(slot, thrown);
            if (primed) {
                armed.add(id);
                cooldowns.prime(player, STRIKE_ID, "Zeus Strike", BossBar.Color.YELLOW);
            }
            return;
        }
        if (primed) {
            cooldowns.start(player, STRIKE_ID, "Zeus Strike", BossBar.Color.YELLOW, STRIKE_COOLDOWN_MS);
        }
    }
}
