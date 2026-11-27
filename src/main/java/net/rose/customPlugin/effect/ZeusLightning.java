package net.rose.customPlugin.effect;

import net.rose.customPlugin.util.DeathMessages;
import net.rose.customPlugin.util.Targets;
import net.rose.customPlugin.util.TrueDamage;
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

/** The Zeus Disc's electricity: the primed real-lightning strike, and the particle spark-chain passive. Players and mobs are both hit. */
public final class ZeusLightning {

    private static final double STRIKE_DAMAGE = 8.0;        // true damage, 4 hearts (the disc itself is 3 hearts)
    private static final double CHAIN_DAMAGE = 1.0;         // half a heart, a normal hit (armor applies)
    private static final double CHAIN_RANGE = 6.0;          // anything this close to a chained target gets zapped too
    private static final int CHAIN_WAVES = 4;               // how many times it can spread outwards
    private static final int CHAIN_MAX_TARGETS = 12;        // per hit, so a crowd can't turn it into a server-wide storm
    private static final long CHAIN_WAVE_DELAY_TICKS = 3L;

    private final JavaPlugin plugin;
    private final DeathMessages deaths;

    public ZeusLightning(JavaPlugin plugin, DeathMessages deaths) {
        this.plugin = plugin;
        this.deaths = deaths;
    }

    /** A real lightning bolt on the target, visual only (no fire, no vanilla damage); the damage is dealt here so it is credited to the thrower. */
    public void strike(Player owner, LivingEntity target) {
        target.getWorld().strikeLightningEffect(target.getLocation());
        zap(owner, target, STRIKE_DAMAGE, DamageType.LIGHTNING_BOLT);
    }

    /**
     * Chain passive: no bolt, just sparks. The hit target (player or mob) arcs sparks out to every player and mob
     * within range of anything it has already reached, and each one it reaches takes half a heart of normal damage.
     * Only the primed strike calls down a lightning bolt, and only the primed strike is true damage.
     */
    public void chain(Player owner, LivingEntity origin) {
        if (!hasTargetNearby(owner, origin)) return; // nothing to jump to: no sparks at all

        Set<UUID> reached = new HashSet<>();
        reached.add(origin.getUniqueId());

        sparks(origin);
        List<LivingEntity> frontier = new ArrayList<>();
        frontier.add(origin);
        wave(owner, frontier, reached, CHAIN_WAVES);
    }

    /** True if some other valid target (player or mob) is within chain range of the hit target. */
    private boolean hasTargetNearby(Player owner, LivingEntity origin) {
        Location centre = origin.getLocation();
        for (LivingEntity candidate : centre.getWorld().getNearbyLivingEntities(centre, CHAIN_RANGE)) {
            if (candidate.getUniqueId().equals(origin.getUniqueId())) continue;
            if (!Targets.isValidTarget(owner, candidate)) continue;
            if (centre.distanceSquared(candidate.getLocation()) > CHAIN_RANGE * CHAIN_RANGE) continue;
            return true;
        }
        return false;
    }

    private void wave(Player owner, List<LivingEntity> frontier, Set<UUID> reached, int wavesLeft) {
        if (wavesLeft <= 0 || frontier.isEmpty() || !owner.isOnline()) return;

        List<LivingEntity> next = new ArrayList<>();
        for (LivingEntity from : frontier) {
            if (reached.size() > CHAIN_MAX_TARGETS) break;
            Location centre = from.getLocation();
            for (LivingEntity candidate : centre.getWorld().getNearbyLivingEntities(centre, CHAIN_RANGE)) {
                if (reached.size() > CHAIN_MAX_TARGETS) break;
                if (reached.contains(candidate.getUniqueId()) || !Targets.isValidTarget(owner, candidate)) continue;
                if (centre.distanceSquared(candidate.getLocation()) > CHAIN_RANGE * CHAIN_RANGE) continue;

                reached.add(candidate.getUniqueId());
                next.add(candidate);

                arc(from, candidate);
                sparks(candidate);
                hit(owner, candidate, CHAIN_DAMAGE);
            }
        }

        if (next.isEmpty() || reached.size() > CHAIN_MAX_TARGETS) return;
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> wave(owner, next, reached, wavesLeft - 1), CHAIN_WAVE_DELAY_TICKS);
    }

    /** A small burst of sparks on a chained target: the passive's version of a hit, with no bolt. */
    private void sparks(LivingEntity target) {
        World world = target.getWorld();
        Location chest = target.getLocation().add(0, target.getHeight() * 0.5, 0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, chest, 18, 0.3, 0.4, 0.3, 0.15);
        world.playSound(chest, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.3f, 1.9f);
    }

    /** A normal hit from the thrower: armor, enchantments, resistance, absorption and i-frames all apply. */
    private void hit(Player owner, LivingEntity target, double damage) {
        deaths.mark(target, owner, DeathMessages.ZEUS_DISC);
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withCausingEntity(owner).withDirectEntity(owner).build();
        target.damage(damage, source);
    }

    private void zap(Player owner, LivingEntity target, double damage, DamageType type) {
        deaths.mark(target, owner, DeathMessages.ZEUS_DISC);
        // True damage; lands even right after the disc hit (ignores i-frames).
        TrueDamage.of(damage).attacker(owner).type(type).apply(target);
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
