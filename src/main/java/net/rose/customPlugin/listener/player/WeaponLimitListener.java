package net.rose.customPlugin.listener.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.item.WeaponLimit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

/**
 * Blocks disabled weapons (see {@link WeaponLimit} for which ones those are): they can't be used for their
 * abilities or to attack.
 *
 * <p>Everything here runs at LOWEST priority, so the weapon listeners (which skip cancelled events) never see a
 * disabled weapon being used.
 */
public final class WeaponLimitListener implements Listener {

    private final WeaponLimit limit;
    private final WeaponLimitNotifier notifier;

    public WeaponLimitListener(WeaponLimit limit, WeaponLimitNotifier notifier) {
        this.limit = limit;
        this.notifier = notifier;
    }

    private void tell(Player player, WeaponLimit.Carried carried) {
        if (carried.duplicate()) {
            player.sendActionBar(Component.text(carried.weapon().name() + " is disabled: you already have one",
                    NamedTextColor.RED));
            return;
        }
        int maxPoints = limit.maxPoints();
        player.sendActionBar(Component.text(carried.weapon().name() + " is disabled: weapon limit reached ("
                + maxPoints + (maxPoints == 1 ? " point)" : " points)"), NamedTextColor.RED));
    }

    // ---- blocking ---------------------------------------------------------------------------------------------------

    /**
     * Only the item use is denied (blocks like chests still work). Listeners that don't use ignoreCancelled already
     * check {@code useItemInHand() == DENY}.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getHand() == null) return;
        WeaponLimit.Carried weapon = limit.disabledCarriedIn(event.getPlayer(), event.getHand());
        if (weapon == null) return;

        event.setUseItemInHand(Event.Result.DENY);
        if (event.getAction().isRightClick()) tell(event.getPlayer(), weapon);
    }

    /** The offhand key is how most weapons are primed, so a disabled weapon in the main hand can't be swapped. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        WeaponLimit.Carried weapon = limit.disabledCarriedIn(event.getPlayer(), EquipmentSlot.HAND);
        if (weapon == null) return;

        event.setCancelled(true);
        tell(event.getPlayer(), weapon);
    }

    /** A disabled weapon doesn't hurt anyone, so its hit effects and passives never trigger either. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        WeaponLimit.Carried weapon = limit.disabledCarriedIn(player, EquipmentSlot.HAND);
        if (weapon == null) return;

        event.setCancelled(true);
        tell(player, weapon);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        WeaponLimit.Carried weapon = limit.disabledCarriedIn(player, event.getHand());
        if (weapon == null) return;

        event.setCancelled(true);
        tell(player, weapon);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident thrown)) return;
        if (!(thrown.getShooter() instanceof Player player)) return;

        EquipmentSlot hand = player.getInventory().getItemInMainHand().isSimilar(thrown.getItemStack())
                ? EquipmentSlot.HAND
                : EquipmentSlot.OFF_HAND;
        WeaponLimit.Carried weapon = limit.disabledCarriedIn(player, hand);
        if (weapon == null) return;

        event.setCancelled(true);
        tell(player, weapon);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        notifier.forget(id);
        limit.onQuit(id);
    }
}
