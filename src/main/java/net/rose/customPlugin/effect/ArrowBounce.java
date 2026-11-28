package net.rose.customPlugin.effect;

import net.rose.customPlugin.util.ArrowHoming;
import net.rose.customPlugin.util.Targets;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The Trickster Bow's bounce chain: an arrow that hits someone spawns a new arrow that flies to the nearest other
 * target, up to a few times, never hitting the same one twice. What an arrow has done is tagged on the arrow itself
 * (persistent data), so nothing is held in a map that could leak.
 */
public final class ArrowBounce {

    private static final double BOUNCE_RANGE = 4.0;          // next target must be within 4 blocks of the one just hit
    private static final double BOUNCE_SPEED = 2.5;
    private static final double BOUNCE_START_OFFSET = 0.9;   // spawn clear of the player that was just hit
    private static final int HOMING_TICKS = 30;              // bounced arrows steer onto their target

    private final JavaPlugin plugin;
    private final ArrowHoming homing;
    private final NamespacedKey bouncesKey;
    private final NamespacedKey hitKey;
    private final NamespacedKey primedKey;

    public ArrowBounce(JavaPlugin plugin, ArrowHoming homing) {
        this.plugin = plugin;
        this.homing = homing;
        this.bouncesKey = new NamespacedKey(plugin, "trickster_bounces");
        this.hitKey = new NamespacedKey(plugin, "trickster_hit");
        this.primedKey = new NamespacedKey(plugin, "trickster_primed");
    }

    /** Marks an arrow as the bow's, with this many bounces left and the targets its chain has already hit. */
    public void tag(Arrow arrow, int bounces, Set<UUID> alreadyHit) {
        PersistentDataContainer data = arrow.getPersistentDataContainer();
        data.set(bouncesKey, PersistentDataType.INTEGER, bounces);
        if (bounces > 0 || (alreadyHit != null && !alreadyHit.isEmpty())) {
            data.set(primedKey, PersistentDataType.BYTE, (byte) 1); // part of the primed ability (fan or bounce)
        }
        if (alreadyHit != null && !alreadyHit.isEmpty()) {
            data.set(hitKey, PersistentDataType.STRING,
                    alreadyHit.stream().map(UUID::toString).collect(Collectors.joining(",")));
        }
    }

    /** Bounces left for a tagged arrow, or null if the arrow is not the bow's. */
    public Integer bouncesLeft(Arrow arrow) {
        return arrow.getPersistentDataContainer().get(bouncesKey, PersistentDataType.INTEGER);
    }

    /** Whether the arrow belongs to a primed shot (the fan or a bounce). */
    public boolean isPrimed(Arrow arrow) {
        return arrow.getPersistentDataContainer().has(primedKey, PersistentDataType.BYTE);
    }

    /** The targets this arrow's bounce chain has already hit (a fresh, mutable set). */
    public Set<UUID> hitBy(Arrow arrow) {
        Set<UUID> hit = new HashSet<>();
        String stored = arrow.getPersistentDataContainer().get(hitKey, PersistentDataType.STRING);
        if (stored != null && !stored.isEmpty()) {
            Arrays.stream(stored.split(",")).map(UUID::fromString).forEach(hit::add);
        }
        return hit;
    }

    /** Spawns the next arrow of the chain, from {@code from} to the nearest target not yet hit. */
    public void bounce(Player shooter, LivingEntity from, int bouncesLeft, double damage, boolean critical,
                       Set<UUID> alreadyHit) {
        if (!shooter.isOnline() || !from.isValid()) return;

        LivingEntity next = nearestTarget(shooter, from, alreadyHit);
        if (next == null) return;

        Location start = from.getLocation().add(0, ArrowHoming.chest(from), 0);
        Vector direction = next.getLocation().add(0, ArrowHoming.chest(next), 0).toVector().subtract(start.toVector());
        double distance = direction.length();
        if (distance < 0.1) return;

        direction.multiply(1.0 / distance);

        World world = from.getWorld();
        Location spawn = start.clone().add(direction.clone().multiply(BOUNCE_START_OFFSET));
        Vector velocity = direction.multiply(BOUNCE_SPEED);

        world.spawn(spawn, Arrow.class, arrow -> {
            arrow.setShooter(shooter);
            arrow.setVelocity(velocity);
            arrow.setDamage(damage);
            arrow.setCritical(critical);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            tag(arrow, bouncesLeft, alreadyHit);
            homing.home(arrow, next, BOUNCE_SPEED, HOMING_TICKS);
        });

        world.playSound(start, Sound.ENTITY_ARROW_SHOOT, 1f, 1.5f);
        world.spawnParticle(Particle.CRIT, start, 12, 0.3, 0.3, 0.3, 0.2);
    }

    /** Runs the bounce a tick later, once the arrow that hit has finished landing. */
    public void bounceNextTick(Player shooter, LivingEntity from, int bouncesLeft, double damage, boolean critical,
                               Set<UUID> alreadyHit) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> bounce(shooter, from, bouncesLeft, damage, critical, alreadyHit));
    }

    /** Closest target, player or mob (not the shooter, not anyone already hit), within range and in line of sight. */
    private LivingEntity nearestTarget(Player shooter, LivingEntity from, Set<UUID> alreadyHit) {
        LivingEntity best = null;
        double bestDistance = BOUNCE_RANGE * BOUNCE_RANGE;
        for (LivingEntity candidate : from.getWorld().getNearbyLivingEntities(from.getLocation(), BOUNCE_RANGE)) {
            if (alreadyHit.contains(candidate.getUniqueId()) || !Targets.isValidTarget(shooter, candidate)) continue;

            double distance = from.getLocation().distanceSquared(candidate.getLocation());
            if (distance > bestDistance || !from.hasLineOfSight(candidate)) continue;

            best = candidate;
            bestDistance = distance;
        }
        return best;
    }
}
