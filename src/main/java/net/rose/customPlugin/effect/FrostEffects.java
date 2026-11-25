package net.rose.customPlugin.effect;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Freezing (frost overlay, slowness, periodic freeze damage) and stun (can't move, attack or use items). */
public final class FrostEffects {

    private static final double FREEZE_DAMAGE = 1.0;      // per pulse, ignores armor
    private static final int FREEZE_FIRST_PULSE = 15;     // after the hit's own invulnerability frames
    private static final int FREEZE_PULSE_INTERVAL = 20;  // then once per second
    private static final int FREEZE_SLOWNESS_AMPLIFIER = 1;

    private static final BlockData ICE = Material.ICE.createBlockData();

    private final JavaPlugin plugin;
    private final Map<UUID, BukkitTask> freezing = new HashMap<>();
    private final Map<UUID, Long> stunnedUntil = new HashMap<>();

    /** True while our own freeze damage is being dealt, so listeners can tell it apart from a real stab. */
    private boolean applyingDamage = false;

    public FrostEffects(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Whether the damage event currently being handled is the freeze's own damage pulse. */
    public boolean isApplyingDamage() {
        return applyingDamage;
    }

    // ------------------------------------------------------------------ Stun

    public void stun(Player victim, int ticks) {
        stunnedUntil.put(victim.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        victim.sendActionBar(Component.text("Stunned!", NamedTextColor.AQUA));
    }

    public boolean isStunned(Player player) {
        Long until = stunnedUntil.get(player.getUniqueId());
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            stunnedUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- Freeze

    /**
     * Freezes the victim: the frost overlay is held at max every tick, they are slowed, and they
     * take {@link #FREEZE_DAMAGE} (freeze damage, credited to the attacker) once per second.
     * Re-freezing a frozen player restarts the timer.
     */
    public void freeze(Player attacker, Player victim, int durationTicks) {
        stopFreeze(victim);

        victim.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, durationTicks, FREEZE_SLOWNESS_AMPLIFIER, false, true, true));

        BukkitTask task = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (!victim.isOnline() || victim.isDead() || ticks >= durationTicks) {
                    stopFreeze(victim);
                    return;
                }

                // Vanilla thaws a few ticks at a time when you're not in powder snow, so keep it full.
                victim.setFreezeTicks(victim.getMaxFreezeTicks());

                if (ticks >= FREEZE_FIRST_PULSE && (ticks - FREEZE_FIRST_PULSE) % FREEZE_PULSE_INTERVAL == 0) {
                    applyingDamage = true;
                    try {
                        victim.damage(FREEZE_DAMAGE,
                                DamageSource.builder(DamageType.FREEZE)
                                        .withCausingEntity(attacker)
                                        .withDirectEntity(attacker)
                                        .build());
                    } finally {
                        applyingDamage = false;
                    }
                }

                if (ticks % 2 == 0) {
                    var at = victim.getLocation().add(0, 1.0, 0);
                    victim.getWorld().spawnParticle(Particle.SNOWFLAKE, at, 6, 0.3, 0.5, 0.3, 0.02);
                    victim.getWorld().spawnParticle(Particle.BLOCK, at, 4, 0.3, 0.6, 0.3, 0.0, ICE);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        freezing.put(victim.getUniqueId(), task);
    }

    private void stopFreeze(Player victim) {
        BukkitTask task = freezing.remove(victim.getUniqueId());
        if (task == null) return;
        task.cancel();
        if (victim.isOnline()) victim.setFreezeTicks(0);
    }

    // --------------------------------------------------------------- Cleanup

    /** Clears all frost state for a player (e.g. on quit). */
    public void clear(Player player) {
        stopFreeze(player);
        stunnedUntil.remove(player.getUniqueId());
    }

    /** Stops every freeze; call from onDisable. */
    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            stopFreeze(player);
        }
        freezing.values().forEach(BukkitTask::cancel);
        freezing.clear();
        stunnedUntil.clear();
    }
}
