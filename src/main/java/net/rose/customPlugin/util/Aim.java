package net.rose.customPlugin.util;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Comparator;

/** Finding who a player (or an arrow) is aiming at, shared by every weapon that needs a target. */
public final class Aim {

    private Aim() {
    }

    /**
     * The first valid target (player or mob, see {@link Targets#isValidTarget}) along a line, stopped by walls.
     * A fat ray ({@code raySize}) means the line only has to pass near the target. Null if there is none.
     */
    public static LivingEntity firstTarget(Player caster, Location from, Vector direction, double range, double raySize) {
        World world = from.getWorld();
        RayTraceResult blocks = world.rayTraceBlocks(from, direction, range, FluidCollisionMode.NEVER, true);
        double reach = blocks == null ? range : blocks.getHitPosition().distance(from.toVector());

        RayTraceResult hit = world.rayTraceEntities(from, direction, reach, raySize, e -> Targets.isValidTarget(caster, e));
        return hit != null && hit.getHitEntity() instanceof LivingEntity target ? target : null;
    }

    /** The first valid target along the player's crosshair, stopped by walls. */
    public static LivingEntity lookedAt(Player player, double range, double raySize) {
        Location eye = player.getEyeLocation();
        return firstTarget(player, eye, eye.getDirection(), range, raySize);
    }

    /**
     * Forgiving aim: first a fat ray along the crosshair (stopped by walls), then, if that finds nothing, the target
     * closest to the crosshair inside a cone around it (it must be in line of sight).
     */
    public static LivingEntity lookedAtOrNear(Player player, double range, double raySize, double coneDegrees) {
        LivingEntity direct = lookedAt(player, range, raySize);
        if (direct != null) return direct;

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        double minDot = Math.cos(Math.toRadians(coneDegrees));
        return player.getWorld().getNearbyLivingEntities(eye, range).stream()
                .filter(e -> Targets.isValidTarget(player, e))
                .filter(e -> {
                    double length = toCentre(eye, e).length();
                    return length > 0.1 && length <= range && dot(eye, direction, e) >= minDot;
                })
                .filter(player::hasLineOfSight)
                .min(Comparator.comparingDouble(e -> -dot(eye, direction, e)))
                .orElse(null);
    }

    private static Vector toCentre(Location eye, LivingEntity entity) {
        return entity.getLocation().add(0, entity.getHeight() * 0.5, 0).toVector().subtract(eye.toVector());
    }

    private static double dot(Location eye, Vector direction, LivingEntity entity) {
        return toCentre(eye, entity).normalize().dot(direction);
    }
}
