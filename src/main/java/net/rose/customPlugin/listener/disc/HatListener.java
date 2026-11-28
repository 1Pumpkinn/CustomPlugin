package net.rose.customPlugin.listener.disc;

import net.rose.customPlugin.effect.DiscFlights;
import net.rose.customPlugin.api.RightClick;
import net.rose.customPlugin.api.HeldWeapon;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.api.AbilityListener;
import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.effect.HatEffects;
import net.rose.customPlugin.effect.ZeusDiscFlight;
import net.rose.customPlugin.item.misc.Hat;
import net.rose.customPlugin.util.DeathMessages;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;


public final class HatListener extends AbilityListener<Hat> {

    /** How fast the thrown hat flies compared to the Zeus Disc (1.0). Lower is slower. */
    private static final double FLIGHT_SPEED_SCALE = 0.6;

    private static final long PAD_COOLDOWN_MS = 20_000;
    private static final long RAZOR_COOLDOWN_MS = 25_000;
    private static final long RAZOR_MISS_COOLDOWN_MS = 8_000; // shorter when the razor hat hits nobody

    private static final String PAD_ID = "hat_bounce";
    private static final String RAZOR_ID = "hat_razor";

    private final HatEffects effects;
    private final DeathMessages deaths;
    private final DiscFlights flights = new DiscFlights();

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
        if (!RightClick.isUse(event) || !weapon.isWeapon(event.getItem())) return;
        if (RightClick.yieldsToBlock(event)) return; // chests, doors, buttons... still work like normal

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        throwDisc(player, event.getHand());
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
        if (flights.isFlying(player) || effects.hasThrown(player)) return; // one hat out at a time

        HeldWeapon held = HeldWeapon.take(player, hand, weapon);
        if (held == null) return;
        try {
            flights.launch(player, onEnd -> ZeusDiscFlight.launch(plugin, null, deaths, onEnd,
                    player, held.item(), held.slot(), false, DeathMessages.HAT, true, FLIGHT_SPEED_SCALE));
        } catch (Throwable t) {
            held.restoreAfterFailure(player, plugin, "Hat", t);
        }
    }

    /** Ends every flight (each hat goes back to its thrower); call from onDisable. */
    public void shutdown() {
        flights.endAll();
    }

    private void throwPad(Player player) {
        if (cooldowns.isOnCooldown(player, PAD_ID)) return; // boss bar already shows the time left
        HeldWeapon taken = HeldWeapon.take(player, EquipmentSlot.HAND, weapon);
        if (taken == null) return;
        try {
            effects.throwPad(player, taken.item(), taken.slot());
        } catch (Throwable t) {
            taken.restoreAfterFailure(player, plugin, "Hat", t);
            return;
        }
        cooldowns.start(player, PAD_ID, "Bounce Hat", BossBar.Color.GREEN, PAD_COOLDOWN_MS);
    }

    private void throwRazor(Player player) {
        if (cooldowns.isOnCooldown(player, RAZOR_ID)) return;
        HeldWeapon taken = HeldWeapon.take(player, EquipmentSlot.HAND, weapon);
        if (taken == null) return;
        try {
            effects.throwRazor(player, taken.item(), taken.slot(), () ->
                    cooldowns.start(player, RAZOR_ID, "Razor Hat", BossBar.Color.RED, RAZOR_MISS_COOLDOWN_MS));
        } catch (Throwable t) {
            taken.restoreAfterFailure(player, plugin, "Hat", t);
            return;
        }
        cooldowns.start(player, RAZOR_ID, "Razor Hat", BossBar.Color.RED, RAZOR_COOLDOWN_MS);
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
        flights.end(player); // a hat in the air would be lost if the player left
        effects.cancel(player);
    }
}
