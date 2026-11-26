package net.rose.customPlugin.listener;

import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.item.TideBlade;
import net.rose.customPlugin.util.CooldownBars;
import net.rose.customPlugin.util.DeathMessages;
import net.rose.customPlugin.util.Targets;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
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

    private static final String DROWN_ID = "drown";

    private final JavaPlugin plugin;
    private final TideBlade tideBlade;
    private final CooldownBars cooldowns;
    private final DeathMessages deaths;

    private final Set<UUID> armed = new HashSet<>();
    private final Map<UUID, BukkitTask> drowning = new HashMap<>();

    public TideBladeListener(JavaPlugin plugin, TideBlade tideBlade, CooldownBars cooldowns, DeathMessages deaths) {
        this.plugin = plugin;
        this.tideBlade = tideBlade;
        this.cooldowns = cooldowns;
        this.deaths = deaths;
    }

    /**
     * Offhand key (F) while holding the Tide Blade:
     * arms Drown. The swap itself is always cancelled.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!tideBlade.isTideBlade(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);

        armDrown(player);
    }

    /** The next target (player or mob) hit with the Tide Blade while armed starts drowning. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!Targets.isValidTarget(attacker, event.getEntity())) return;
        LivingEntity victim = (LivingEntity) event.getEntity();
        if (!armed.contains(attacker.getUniqueId())) return;
        if (!tideBlade.isTideBlade(attacker.getInventory().getItemInMainHand())) return;

        armed.remove(attacker.getUniqueId());
        cooldowns.unprime(attacker, DROWN_ID);
        cooldowns.start(attacker, DROWN_ID, "Drown", BossBar.Color.BLUE, DROWN_COOLDOWN_MS);
        startDrowning(attacker, victim);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        armed.remove(event.getEntity().getUniqueId());
        cooldowns.unprime(event.getEntity(), DROWN_ID);
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
        if (!armed.add(player.getUniqueId())) return;
        cooldowns.prime(player, DROWN_ID, "Drown", BossBar.Color.BLUE);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1f, 0.8f);
    }

    private void startDrowning(Player attacker, LivingEntity victim) {
        stopDrowning(victim);

        BukkitTask task = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (Targets.isGone(victim) || ticks >= DROWN_DURATION_TICKS) {
                    stopDrowning(victim);
                    return;
                }

                // Air bar drains to empty, then stays empty
                int max = victim.getMaximumAir();
                victim.setRemainingAir(Math.max(0, max - (max * ticks / AIR_DRAIN_TICKS)));

                if (ticks >= AIR_DRAIN_TICKS && (ticks - AIR_DRAIN_TICKS) % DROWN_DAMAGE_INTERVAL == 0) {
                    // Drowning damage credited to the attacker, so kills count as theirs.
                    // The DROWN damage type already plays the drowning hurt sound itself.
                    deaths.mark(victim, attacker, DeathMessages.TIDE_BLADE);
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

    private void stopDrowning(LivingEntity victim) {
        BukkitTask task = drowning.remove(victim.getUniqueId());
        if (task == null) return;
        task.cancel();
        if (!Targets.isGone(victim)) {
            victim.setRemainingAir(victim.getMaximumAir());
        }
    }
}
