package net.rose.customPlugin.listener;

import net.rose.customPlugin.effect.FrostEffects;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** Enforces stun: no walking or jumping (looking and falling are fine), no attacking, no items or abilities. */
public final class StunListener implements Listener {

    private final FrostEffects frost;

    public StunListener(FrostEffects frost) {
        this.frost = frost;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!frost.isStunned(player)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        boolean moved = to.getX() != from.getX() || to.getZ() != from.getZ() || to.getY() > from.getY();
        if (!moved) return;

        Location locked = to.clone();
        locked.setX(from.getX());
        locked.setZ(from.getZ());
        if (to.getY() > from.getY()) locked.setY(from.getY());
        event.setTo(locked);
    }

    // Lowest priority so weapon listeners (which ignore cancelled events) never see the input.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (frost.isStunned(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (frost.isStunned(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (event.getDamager() instanceof Player attacker && frost.isStunned(attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        frost.clear(event.getPlayer());
    }
}
