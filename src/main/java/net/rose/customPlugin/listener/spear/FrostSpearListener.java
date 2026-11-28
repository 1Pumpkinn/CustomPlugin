package net.rose.customPlugin.listener.spear;

import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.api.AbilityListener;
import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.effect.FrostEffects;
import net.rose.customPlugin.item.spear.FrostSpear;
import net.rose.customPlugin.util.Targets;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class FrostSpearListener extends AbilityListener<FrostSpear> {

    // --- Dash ---
    private static final long DASH_COOLDOWN_MS = 25_000;
    private static final int DASH_TICKS = 8;
    private static final double DASH_SPEED = 1.2;           // blocks per tick, ~9 blocks total
    private static final double DASH_HIT_EXPAND = 1.0;      // how far around you counts as "dashed into"
    private static final int DASH_FREEZE_TICKS = 20;        // 1s
    private static final int DASH_STUN_TICKS = 50;          // 2.5s

    private static final int TRAIL_LINGER_TICKS = 30;       // how long the stun area stays marked after the dash
    private static final int TRAIL_REFRESH_TICKS = 2;

    // --- Passive ---
    private static final int PASSIVE_FREEZE_TICKS = 20;     // 1s

    private static final String DASH_ID = "frost_dash";

    private final FrostEffects frost;

    public FrostSpearListener(WeaponContext context, FrostSpear spear, FrostEffects frost) {
        super(context, spear);
        this.frost = frost;
    }

    /** Offhand key (F) while holding the Frost Spear dashes forward. The swap itself is always cancelled. */
    @Override
    protected void onOffhand(Player player, boolean sneaking) {
        dash(player);
    }

    /** Passive: stabbing a player with the spear chills them (frost overlay and slowness, no damage). Only melee hits count, so the freeze's own damage can't retrigger it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStab(EntityDamageByEntityEvent event) {
        if (frost.isApplyingDamage()) return; // the freeze's own damage pulse must not re-freeze (it can be reported as ENTITY_ATTACK)
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!Targets.isValidTarget(attacker, event.getEntity())) return;
        if (!weapon.isWeapon(attacker.getInventory().getItemInMainHand())) return;

        frost.chill((LivingEntity) event.getEntity(), PASSIVE_FREEZE_TICKS);
    }

    /** A chilled player holds a full frost overlay, which makes vanilla deal freeze damage; the passive must not hurt. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFreezeDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FREEZE) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (frost.isApplyingDamage()) return;
        if (frost.isHarmlessFreeze(living)) event.setCancelled(true);
    }

    // ------------------------------------------------------------------ Dash

    /** Leaves snowflakes along the whole dash path for a moment, showing where players get stunned. */
    private void markStunArea(World world, List<Location> trail) {
        if (trail.isEmpty()) return;

        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks >= TRAIL_LINGER_TICKS) {
                    cancel();
                    return;
                }
                if (ticks % TRAIL_REFRESH_TICKS == 0) {
                    for (Location point : trail) {
                        world.spawnParticle(Particle.SNOWFLAKE, point, 4,
                                DASH_HIT_EXPAND * 0.8, 0.9, DASH_HIT_EXPAND * 0.8, 0.0);
                        world.spawnParticle(Particle.CLOUD, point, 1,
                                DASH_HIT_EXPAND * 0.6, 0.6, DASH_HIT_EXPAND * 0.6, 0.0);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void dash(Player player) {
        if (cooldowns.isOnCooldown(player, DASH_ID)) return; // boss bar already shows the time left

        cooldowns.start(player, DASH_ID, "Dash", BossBar.Color.BLUE, DASH_COOLDOWN_MS);

        Vector direction = player.getEyeLocation().getDirection().normalize();
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.2f);

        new BukkitRunnable() {
            private int ticks = 0;
            private final Set<UUID> hit = new HashSet<>();
            private final List<Location> trail = new ArrayList<>();

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ticks >= DASH_TICKS) {
                    if (player.isOnline() && !player.isDead()) {
                        player.setVelocity(direction.clone().multiply(0.25)); // bleed the speed off
                    }
                    markStunArea(world, trail);
                    cancel();
                    return;
                }

                trail.add(player.getLocation().add(0, 1.0, 0));

                Vector velocity = direction.clone().multiply(DASH_SPEED);
                if (velocity.getY() < 0.1 && player.isOnGround()) velocity.setY(0.1); // don't scrape the floor
                player.setVelocity(velocity);

                BoundingBox box = player.getBoundingBox().expand(DASH_HIT_EXPAND);
                for (Entity entity : player.getWorld().getNearbyEntities(box)) {
                    if (!Targets.isValidTarget(player, entity)) continue;
                    if (!hit.add(entity.getUniqueId())) continue;

                    LivingEntity victim = (LivingEntity) entity;
                    frost.freeze(player, victim, DASH_FREEZE_TICKS);
                    frost.stun(victim, DASH_STUN_TICKS);
                    victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.4f);
                    victim.getWorld().spawnParticle(Particle.SNOWFLAKE,
                            victim.getLocation().add(0, 1.0, 0), 25, 0.4, 0.6, 0.4, 0.05);
                }

                player.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        player.getLocation().add(0, 1.0, 0), 8, 0.3, 0.5, 0.3, 0.02);
                player.getWorld().spawnParticle(Particle.CLOUD,
                        player.getLocation().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0.01);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
