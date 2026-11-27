package net.rose.customPlugin.effect;

import net.rose.customPlugin.util.DeathMessages;
import net.rose.customPlugin.util.Targets;
import net.rose.customPlugin.util.TrueDamage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Freezing (frost overlay, slowness, periodic freeze damage) and stun (can't move, attack or use items).
 * Works on players and mobs; a stunned mob has its AI switched off for the duration.
 */
public final class FrostEffects {

    private static final double FREEZE_DAMAGE = 1.0;      // per pulse, ignores armor
    private static final int FREEZE_FIRST_PULSE = 15;     // after the hit's own invulnerability frames
    private static final int FREEZE_PULSE_INTERVAL = 20;  // then once per second
    private static final int FREEZE_SLOWNESS_AMPLIFIER = 1;

    private static final BlockData ICE = Material.ICE.createBlockData();

    private final JavaPlugin plugin;
    private final DeathMessages deaths;
    private final Map<UUID, BukkitTask> freezing = new HashMap<>();
    private final Map<UUID, Long> stunnedUntil = new HashMap<>();
    private final Set<UUID> harmlessFreeze = new HashSet<>();

    /** True while our own freeze damage is being dealt, so listeners can tell it apart from a real stab. */
    private boolean applyingDamage = false;

    public FrostEffects(JavaPlugin plugin, DeathMessages deaths) {
        this.plugin = plugin;
        this.deaths = deaths;
    }

    /** Whether the damage event currently being handled is the freeze's own damage pulse. */
    public boolean isApplyingDamage() {
        return applyingDamage;
    }

    /** Whether the entity is under a chill (frost overlay and slowness only), so any freeze damage on it should be cancelled. */
    public boolean isHarmlessFreeze(LivingEntity player) {
        return harmlessFreeze.contains(player.getUniqueId());
    }

    // ------------------------------------------------------------------ Stun

    public void stun(LivingEntity victim, int ticks) {
        stunnedUntil.put(victim.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        if (victim instanceof Mob mob) {
            // No AI: it can't walk, target or attack. Gravity and knockback still work.
            mob.setAware(false);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // A newer stun may have extended it; only the last one switches the AI back on.
                if (mob.isValid() && !isStunned(mob)) mob.setAware(true);
            }, ticks + 1L);
        }
    }

    public boolean isStunned(LivingEntity player) {
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
    public void freeze(Player attacker, LivingEntity victim, int durationTicks) {
        startFreeze(attacker, victim, durationTicks, true);
    }

    /**
     * Chills the victim: the frost overlay is held at max every tick and they are slowed, but they
     * take no freeze damage at all (neither ours nor vanilla's). Re-chilling restarts the timer.
     */
    public void chill(LivingEntity victim, int durationTicks) {
        startFreeze(null, victim, durationTicks, false);
    }

    private void startFreeze(Player attacker, LivingEntity victim, int durationTicks, boolean damaging) {
        stopFreeze(victim);

        if (!damaging) harmlessFreeze.add(victim.getUniqueId());

        victim.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, durationTicks, FREEZE_SLOWNESS_AMPLIFIER, false, true, true));

        BukkitTask task = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (Targets.isGone(victim) || ticks >= durationTicks) {
                    stopFreeze(victim);
                    return;
                }

                // Vanilla thaws a few ticks at a time when you're not in powder snow, so keep it full.
                victim.setFreezeTicks(victim.getMaxFreezeTicks());

                if (damaging && ticks >= FREEZE_FIRST_PULSE && (ticks - FREEZE_FIRST_PULSE) % FREEZE_PULSE_INTERVAL == 0) {
                    applyingDamage = true;
                    try {
                        deaths.mark(victim, attacker, DeathMessages.FROST_SPEAR);
                        TrueDamage.of(FREEZE_DAMAGE).attacker(attacker).type(DamageType.FREEZE).apply(victim);
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

    private void stopFreeze(LivingEntity victim) {
        harmlessFreeze.remove(victim.getUniqueId());
        BukkitTask task = freezing.remove(victim.getUniqueId());
        if (task == null) return;
        task.cancel();
        if (victim.isValid()) victim.setFreezeTicks(0);
    }

    // --------------------------------------------------------------- Cleanup

    /** Clears all frost state for a player (e.g. on quit). */
    public void clear(Player player) {
        stopFreeze(player);
        stunnedUntil.remove(player.getUniqueId());
    }

    /** Stops every freeze; call from onDisable. */
    public void shutdown() {
        for (UUID id : new ArrayList<>(freezing.keySet())) {
            if (Bukkit.getEntity(id) instanceof LivingEntity living) stopFreeze(living);
        }
        for (UUID id : new ArrayList<>(stunnedUntil.keySet())) {
            if (Bukkit.getEntity(id) instanceof Mob mob) mob.setAware(true);
        }
        freezing.values().forEach(BukkitTask::cancel);
        freezing.clear();
        harmlessFreeze.clear();
        stunnedUntil.clear();
    }
}
