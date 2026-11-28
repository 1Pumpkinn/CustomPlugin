package net.rose.customPlugin.listener.trident;

import net.rose.customPlugin.api.PrimedAbility;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.api.AbilityListener;
import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.effect.TempestSpinEffect;
import net.rose.customPlugin.item.trident.TempestTrident;
import net.rose.customPlugin.util.Targets;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TempestTridentListener extends AbilityListener<TempestTrident> {

    private static final long THROW_COOLDOWN_MS = 30_000;
    /** A trident that never reports a hit (void, despawn, unloaded chunk) is forgotten after this long. */
    private static final long EMPOWERED_TIMEOUT_MS = 5 * 60_000L;

    private record Empowered(UUID thrower, long at) {
    }

    private final TempestSpinEffect spin;

    private final PrimedAbility throwing;
    /** Tridents in flight that carry the ability: trident entity id -> who threw it. */
    private final Map<UUID, Empowered> empowered = new HashMap<>();

    public TempestTridentListener(WeaponContext context, TempestTrident trident, TempestSpinEffect spin) {
        super(context, trident);
        this.spin = spin;
        this.throwing = primedAbility("tempest_throw", "Tempest", BossBar.Color.BLUE, THROW_COOLDOWN_MS);
    }

    /** Offhand key (F) while holding the Tempest Trident primes the next throw. The swap itself is always cancelled. */
    @Override
    protected void onOffhand(Player player, boolean sneaking) {
        if (throwing.prime(player)) player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.6f);
    }

    /** The next Tempest Trident the player throws after priming carries the ability. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident thrown)) return;
        if (!(thrown.getShooter() instanceof Player player)) return;
        if (!weapon.isWeapon(thrown.getItemStack())) return;
        if (!throwing.spend(player)) return;

        long now = System.currentTimeMillis();
        empowered.values().removeIf(entry -> now - entry.at() > EMPOWERED_TIMEOUT_MS);
        empowered.put(thrown.getUniqueId(), new Empowered(player.getUniqueId(), now));
    }

    /** Hitting a player or mob starts the spin and hands the trident back; a miss just uses up the charge. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident thrown)) return;
        Empowered entry = empowered.remove(thrown.getUniqueId());
        if (entry == null) return;

        Player thrower = plugin.getServer().getPlayer(entry.thrower());
        Entity hit = event.getHitEntity();
        if (thrower == null || !(hit instanceof LivingEntity victim) || !Targets.isValidTarget(thrower, victim)) return;

        thrown.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        ItemStack returned = thrown.getItemStack().clone();
        Location hitAt = thrown.getLocation();

        // One tick later, after vanilla has finished handling the hit.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (thrown.isValid()) thrown.remove();
            if (!thrower.isOnline()) {
                // The thrower left in that tick: the trident must not vanish with them.
                if (thrower.getGameMode() != GameMode.CREATIVE) hitAt.getWorld().dropItemNaturally(hitAt, returned);
                return;
            }

            spin.start(thrower, victim);
            if (thrower.getGameMode() == GameMode.CREATIVE) return; // creative throws never used the item up
            for (ItemStack leftover : thrower.getInventory().addItem(returned).values()) {
                thrower.getWorld().dropItemNaturally(thrower.getLocation(), leftover);
            }
        });
    }

}
