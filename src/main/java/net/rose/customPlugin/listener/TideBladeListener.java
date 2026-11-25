package net.rose.customPlugin.listener;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.item.TideBlade;
import net.rose.customPlugin.util.CooldownBars;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TideBladeListener implements Listener {

    // --- Drown ---
    private static final int DROWN_DURATION_TICKS = 160;     // 8s total
    private static final int AIR_DRAIN_TICKS = 60;           // air bar empties over the first 3s
    private static final int DROWN_DAMAGE_INTERVAL = 20;     // then damage once per second
    private static final double DROWN_DAMAGE = 1.5;          // 5 ticks of damage = 7.5 HP total
    private static final long DROWN_COOLDOWN_MS = 30_000;

    // --- Geyser ---
    private static final int GEYSER_RANGE = 20;
    private static final double GEYSER_LAUNCH_VELOCITY = 1.2;
    private static final int GEYSER_COLUMN_TICKS = 12;
    private static final long GEYSER_COOLDOWN_MS = 20_000;

    private static final String DROWN_ID = "drown";
    private static final String GEYSER_ID = "geyser";

    private final JavaPlugin plugin;
    private final TideBlade tideBlade;
    private final CooldownBars cooldowns;

    private final Set<UUID> armed = new HashSet<>();
    private final Map<UUID, BukkitTask> drowning = new HashMap<>();

    public TideBladeListener(JavaPlugin plugin, TideBlade tideBlade, CooldownBars cooldowns) {
        this.plugin = plugin;
        this.tideBlade = tideBlade;
        this.cooldowns = cooldowns;
    }

    /**
     * Offhand key (F) while holding the Tide Blade:
     * normal = arm Drown, crouching = Geyser. The swap itself is always cancelled.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!tideBlade.isTideBlade(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);

        if (player.isSneaking()) {
            useGeyser(player);
        } else {
            armDrown(player);
        }
    }

    /** The next player hit with the Tide Blade while armed starts drowning. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!armed.contains(attacker.getUniqueId())) return;
        if (!tideBlade.isTideBlade(attacker.getInventory().getItemInMainHand())) return;

        armed.remove(attacker.getUniqueId());
        cooldowns.start(attacker, DROWN_ID, "Drown", BossBar.Color.BLUE, DROWN_COOLDOWN_MS);
        startDrowning(attacker, victim);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        armed.remove(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cooldowns.resume(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        armed.remove(player.getUniqueId());
        stopDrowning(player);
        cooldowns.hideAll(player);
    }

    // ------------------------------------------------------------------ Drown

    private void armDrown(Player player) {
        if (cooldowns.isOnCooldown(player, DROWN_ID)) return; // boss bar already shows the time left
        if (!armed.add(player.getUniqueId())) {
            player.sendActionBar(Component.text("Drown is already ready", NamedTextColor.AQUA));
            return;
        }
        player.sendActionBar(Component.text("Drown ready - hit a player", NamedTextColor.AQUA));
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1f, 0.8f);
    }

    private void startDrowning(Player attacker, Player victim) {
        stopDrowning(victim);

        BukkitTask task = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (!victim.isOnline() || victim.isDead() || ticks >= DROWN_DURATION_TICKS) {
                    stopDrowning(victim);
                    return;
                }

                // Air bar drains to empty, then stays empty
                int max = victim.getMaximumAir();
                victim.setRemainingAir(Math.max(0, max - (max * ticks / AIR_DRAIN_TICKS)));

                if (ticks >= AIR_DRAIN_TICKS && (ticks - AIR_DRAIN_TICKS) % DROWN_DAMAGE_INTERVAL == 0) {
                    // Drowning damage credited to the attacker, so kills count as theirs.
                    // The DROWN damage type already plays the drowning hurt sound itself.
                    victim.damage(DROWN_DAMAGE,
                            DamageSource.builder(DamageType.DROWN)
                                    .withCausingEntity(attacker)
                                    .withDirectEntity(attacker)
                                    .build());
                }

                if (ticks % 4 == 0) {
                    victim.getWorld().spawnParticle(Particle.BUBBLE,
                            victim.getEyeLocation(), 4, 0.25, 0.15, 0.25, 0.02);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        drowning.put(victim.getUniqueId(), task);
    }

    private void stopDrowning(Player victim) {
        BukkitTask task = drowning.remove(victim.getUniqueId());
        if (task == null) return;
        task.cancel();
        if (victim.isOnline() && !victim.isDead()) {
            victim.setRemainingAir(victim.getMaximumAir());
        }
    }

    // ----------------------------------------------------------------- Geyser

    private void useGeyser(Player player) {
        if (cooldowns.isOnCooldown(player, GEYSER_ID)) return; // boss bar already shows the time left

        Entity looking = player.getTargetEntity(GEYSER_RANGE);
        if (!(looking instanceof Player target)) {
            player.sendActionBar(Component.text("Look at a player to use Geyser", NamedTextColor.RED));
            return;
        }

        cooldowns.start(player, GEYSER_ID, "Geyser", BossBar.Color.WHITE, GEYSER_COOLDOWN_MS);

        Location base = target.getLocation();
        target.setVelocity(new Vector(0, GEYSER_LAUNCH_VELOCITY, 0));
        base.getWorld().playSound(base, Sound.ENTITY_GENERIC_SPLASH, 1.5f, 0.6f);

        new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                if (tick >= GEYSER_COLUMN_TICKS) {
                    cancel();
                    return;
                }
                Location at = base.clone().add(0, tick * 0.7, 0);
                at.getWorld().spawnParticle(Particle.SPLASH, at, 25, 0.35, 0.2, 0.35, 0.1);
                at.getWorld().spawnParticle(Particle.BUBBLE_POP, at, 8, 0.3, 0.2, 0.3, 0.02);
                at.getWorld().spawnParticle(Particle.CLOUD, at, 2, 0.2, 0.1, 0.2, 0.01);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
