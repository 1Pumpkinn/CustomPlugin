package net.rose.customPlugin.listener.disc;

import net.rose.customPlugin.effect.DiscFlights;
import net.rose.customPlugin.api.RightClick;
import net.rose.customPlugin.api.PrimedAbility;
import net.rose.customPlugin.api.HeldWeapon;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;


public final class ZeusDiscListener extends AbilityListener<ZeusDisc> {

    private static final long STRIKE_COOLDOWN_MS = 20_000;

    private final ZeusLightning lightning;
    private final DeathMessages deaths;
    private final PrimedAbility strike;
    private final DiscFlights flights = new DiscFlights();

    public ZeusDiscListener(WeaponContext context, ZeusDisc disc, ZeusLightning lightning) {
        super(context, disc);
        this.lightning = lightning;
        this.deaths = context.deaths();
        this.strike = primedAbility("zeus_strike", "Zeus Strike", BossBar.Color.YELLOW, STRIKE_COOLDOWN_MS);
    }

    /** Offhand key (F) while holding the Zeus Disc primes the next throw. The swap itself is always cancelled. */
    @Override
    protected void onOffhand(Player player, boolean sneaking) {
        if (strike.prime(player)) {
            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
        }
    }

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

    /** A disc in the air would be lost if the player left, so it comes home first. */
    @Override
    protected void onPlayerQuit(Player player) {
        flights.end(player);
    }

    /** Ends every flight (each disc goes back to its thrower); call from onDisable. */
    public void shutdown() {
        flights.endAll();
    }

    /** Takes the disc out of the player's hand and throws it. */
    private void throwDisc(Player player, EquipmentSlot hand) {
        if (flights.isFlying(player)) return; // one disc, one flight

        HeldWeapon held = HeldWeapon.take(player, hand, weapon);
        if (held == null) return;

        boolean primed = strike.consume(player);
        try {
            flights.launch(player, onEnd -> ZeusDiscFlight.launch(plugin, lightning, deaths, onEnd,
                    player, held.item(), held.slot(), primed, DeathMessages.ZEUS_DISC, false, 1.0));
        } catch (Throwable t) {
            held.restoreAfterFailure(player, plugin, "Zeus Disc", t);
            if (primed) strike.restore(player);
            return;
        }
        if (primed) strike.startCooldown(player);
    }
}
