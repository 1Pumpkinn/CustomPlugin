package net.rose.customPlugin.listener;

import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.effect.FrostEffects;
import net.rose.customPlugin.item.FrostSpear;
import net.rose.customPlugin.util.CooldownBars;
import net.rose.customPlugin.util.Targets;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class FrostSpearListener implements Listener {

    // --- Dash ---
    private static final long DASH_COOLDOWN_MS = 5_000;
    private static final int DASH_TICKS = 8;
    private static final double DASH_SPEED = 1.2;           // blocks per tick, ~9 blocks total
    private static final double DASH_HIT_EXPAND = 1.0;      // how far around you counts as "dashed into"
    private static final int DASH_FREEZE_TICKS = 20;        // 1s
    private static final int DASH_STUN_TICKS = 30;          // 1.5s

    // --- Passive ---
    private static final int PASSIVE_FREEZE_TICKS = 20;     // 1s

    private static final String DASH_ID = "frost_dash";

    private final JavaPlugin plugin;
    private final FrostSpear spear;
    private final CooldownBars cooldowns;
    private final FrostEffects frost;

    public FrostSpearListener(JavaPlugin plugin, FrostSpear spear, CooldownBars cooldowns, FrostEffects frost) {
        this.plugin = plugin;
        this.spear = spear;
        this.cooldowns = cooldowns;
        this.frost = frost;
    }

    /** Offhand key (F) while holding the Frost Spear dashes forward. The swap itself is always cancelled. */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!spear.isFrostSpear(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);
        dash(player);
    }

    /** Passive: stabbing a player with the spear freezes them. Only melee hits count, so the freeze's own damage can't retrigger it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStab(EntityDamageByEntityEvent event) {
        if (frost.isApplyingDamage()) return; // the freeze's own damage pulse must not re-freeze (it can be reported as ENTITY_ATTACK)
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!Targets.isValidPlayer(attacker, event.getEntity())) return;
        if (!spear.isFrostSpear(attacker.getInventory().getItemInMainHand())) return;

        frost.freeze(attacker, (Player) event.getEntity(), PASSIVE_FREEZE_TICKS);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cooldowns.resume(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.hideAll(event.getPlayer());
    }

    // ------------------------------------------------------------------ Dash

    private void dash(Player player) {
        if (cooldowns.isOnCooldown(player, DASH_ID)) return; // boss bar already shows the time left

        cooldowns.start(player, DASH_ID, "Dash", BossBar.Color.BLUE, DASH_COOLDOWN_MS);

        Vector direction = player.getEyeLocation().getDirection().normalize();
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.2f);

        new BukkitRunnable() {
            private int ticks = 0;
            private final Set<UUID> hit = new HashSet<>();

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ticks >= DASH_TICKS) {
                    if (player.isOnline() && !player.isDead()) {
                        player.setVelocity(direction.clone().multiply(0.25)); // bleed the speed off
                    }
                    cancel();
                    return;
                }

                Vector velocity = direction.clone().multiply(DASH_SPEED);
                if (velocity.getY() < 0.1 && player.isOnGround()) velocity.setY(0.1); // don't scrape the floor
                player.setVelocity(velocity);

                BoundingBox box = player.getBoundingBox().expand(DASH_HIT_EXPAND);
                for (Entity entity : player.getWorld().getNearbyEntities(box)) {
                    if (!Targets.isValidPlayer(player, entity)) continue;
                    if (!hit.add(entity.getUniqueId())) continue;

                    Player victim = (Player) entity;
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
