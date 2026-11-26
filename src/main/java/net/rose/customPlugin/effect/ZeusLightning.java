package net.rose.customPlugin.effect;

import net.rose.customPlugin.util.Targets;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The Zeus Disc's lightning: the primed strike and the chain lightning passive. Players and mobs are both hit. */
public final class ZeusLightning {

    private static final double STRIKE_DAMAGE = 5.0;        // same as a vanilla bolt, 2.5 hearts
    private static final double CHAIN_DAMAGE = 1.0;         // half a heart
    private static final double CHAIN_RANGE = 6.0;          // anything this close to a chained target gets zapped too
    private static final int CHAIN_WAVES = 4;               // how many times it can spread outwards
    private static final int CHAIN_MAX_TARGETS = 12;        // per hit, so a crowd can't turn it into a server-wide storm
    private static final long CHAIN_WAVE_DELAY_TICKS = 3L;

    private final JavaPlugin plugin;

    public ZeusLightning(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** A bolt on the target: visual only, then the damage is dealt here so it is credited to the thrower. */
    public void strike(Player owner, LivingEntity target) {
        bolt(target.getLocation());
        zap(owner, target, STRIKE_DAMAGE, DamageType.LIGHTNING_BOLT);
    }

    /**
     * Chain lightning: a bolt (particles only) on the hit target, then it spreads
     * outwards to every player and mob within range of anything it has already reached.
     * Each one it reaches takes half a heart.
     */
    public void chain(Player owner, LivingEntity origin) {
        if (!hasPlayerNearby(owner, origin)) return; // nobody to jump to: no lightning at all

        Set<UUID> reached = new HashSet<>();
        reached.add(origin.getUniqueId());

        bolt(origin.getLocation());
        List<LivingEntity> frontier = new ArrayList<>();
        frontier.add(origin);
        wave(owner, frontier, reached, CHAIN_WAVES);
    }

    /** True if some other valid player is within chain range of the hit target. */
    private boolean hasPlayerNearby(Player owner, LivingEntity origin) {
        Location centre = origin.getLocation();
        for (Player candidate : centre.getWorld().getNearbyPlayers(centre, CHAIN_RANGE)) {
            if (candidate.getUniqueId().equals(origin.getUniqueId())) continue;
            if (!Targets.isValidPlayer(owner, candidate)) continue;
            if (centre.distanceSquared(candidate.getLocation()) > CHAIN_RANGE * CHAIN_RANGE) continue;
            return true;
        }
        return false;
    }

    private void wave(Player owner, List<LivingEntity> frontier, Set<UUID> reached, int wavesLeft) {
        if (wavesLeft <= 0 || frontier.isEmpty() || !owner.isOnline()) return;

        List<LivingEntity> next = new ArrayList<>();
        for (LivingEntity from : frontier) {
            Location centre = from.getLocation();
            for (Player candidate : centre.getWorld().getNearbyPlayers(centre, CHAIN_RANGE)) {
                if (reached.size() > CHAIN_MAX_TARGETS) break;
                if (reached.contains(candidate.getUniqueId()) || !Targets.isValidPlayer(owner, candidate)) continue;
                if (centre.distanceSquared(candidate.getLocation()) > CHAIN_RANGE * CHAIN_RANGE) continue;

                reached.add(candidate.getUniqueId());
                next.add(candidate);

                arc(from, candidate);
                bolt(candidate.getLocation());
                zap(owner, candidate, CHAIN_DAMAGE, DamageType.MAGIC); // magic ignores armor, so it really is half a heart
            }
        }

        if (next.isEmpty()) return;
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> wave(owner, next, reached, wavesLeft - 1), CHAIN_WAVE_DELAY_TICKS);
    }

    /** A lightning-looking strike made only of particles and sound: no real bolt entity, no fire, no block damage. */
    private void bolt(Location at) {
        World world = at.getWorld();
        // A jagged column of sparks from the sky down to the target.
        for (double y = 0; y <= 12; y += 0.5) {
            double jitterX = (Math.random() - 0.5) * 0.5;
            double jitterZ = (Math.random() - 0.5) * 0.5;
            world.spawnParticle(Particle.ELECTRIC_SPARK, at.getX() + jitterX, at.getY() + y, at.getZ() + jitterZ,
                    2, 0.05, 0.05, 0.05, 0.0);
        }
        world.spawnParticle(Particle.END_ROD, at.clone().add(0, 1.0, 0), 12, 0.3, 0.8, 0.3, 0.05);
        world.spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 0.2, 0), 30, 0.6, 0.2, 0.6, 0.2);
        world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.8f, 1.4f);
    }

    private void zap(Player owner, LivingEntity target, double damage, DamageType type) {
        target.setNoDamageTicks(0); // the bolt lands even right after the disc hit
        target.damage(damage, DamageSource.builder(type)
                .withCausingEntity(owner)
                .withDirectEntity(owner)
                .build());
    }

    /** Sparks along the line between two entities. */
    private void arc(LivingEntity from, LivingEntity to) {
        World world = from.getWorld();
        Vector start = from.getLocation().add(0, from.getHeight() * 0.5, 0).toVector();
        Vector end = to.getLocation().add(0, to.getHeight() * 0.5, 0).toVector();
        Vector step = end.clone().subtract(start);
        double length = step.length();
        if (length < 0.1) return;
        step.multiply(1.0 / length);

        for (double d = 0; d <= length; d += 0.35) {
            Vector point = start.clone().add(step.clone().multiply(d));
            world.spawnParticle(Particle.ELECTRIC_SPARK, point.getX(), point.getY(), point.getZ(),
                    2, 0.12, 0.12, 0.12, 0.02);
        }
        world.playSound(to.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.4f, 1.8f);
    }
}
