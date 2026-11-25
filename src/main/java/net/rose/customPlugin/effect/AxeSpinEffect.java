package net.rose.customPlugin.effect;

import net.rose.customPlugin.util.Targets;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A flaming axe that orbits the caster like a rotating arm (handle towards you, head outward),
 * damaging and igniting every player it touches.
 *
 * <p>The display entity just follows the caster (always yaw 0 / pitch 0, so looking up or down
 * never tilts it). The orbit itself is done with the display's transformation, which the client
 * interpolates every frame, so it is smooth instead of being driven by per-tick teleports.
 */
public final class AxeSpinEffect {

    private static final int DURATION_TICKS = 100;                    // 5s
    private static final double ORBIT_RADIUS = 2.4;                   // distance of the axe's centre
    private static final float AXE_SCALE = 1.8f;
    private static final double HIT_RADIUS = 1.4;
    // ~1 orbit per second. Negative = counter-clockwise from above, which makes the blade edge lead.
    // Flip the sign if the blade ever looks like it's trailing again.
    private static final double SPIN_PER_TICK = -Math.toRadians(18);
    private static final double HIT_DAMAGE = 1.5;
    private static final int BURN_TICKS = 80;                         // 4s
    private static final int HIT_INTERVAL_TICKS = 15;                 // per victim

    /**
     * Turns the axe about its own centre in 90 degree steps if the head/blade points the wrong
     * way (0 = head outward, 180 = head inward, 90 / -90 = sideways).
     */
    private static final double FACING_OFFSET_DEGREES = 0;

    private record Active(BukkitTask task, ItemDisplay display) {
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Active> active = new HashMap<>();

    public AxeSpinEffect(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(Player caster) {
        stop(caster);

        World world = caster.getWorld();
        ItemDisplay display = world.spawn(anchor(caster), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.NETHERITE_AXE));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(transform(0));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setInterpolationDuration(2);
            d.setShadowRadius(0f);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });

        world.playSound(caster.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);

        BukkitTask task = new BukkitRunnable() {
            private int ticks = 0;
            private double angle = 0;
            private final Map<UUID, Integer> lastHit = new HashMap<>();

            @Override
            public void run() {
                if (!caster.isOnline() || caster.isDead() || ticks >= DURATION_TICKS
                        || !display.isValid() || !display.getWorld().equals(caster.getWorld())) {
                    stop(caster);
                    return;
                }

                Location centre = anchor(caster);
                double previous = angle;
                angle += SPIN_PER_TICK;

                // Follow the caster, and let the client interpolate the orbit + rotation.
                display.teleport(centre);
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(2);
                display.setTransformation(transform(angle));

                // Check the swept arc this tick (halfway + end) so a fast axe can't skip over anyone.
                Location pos = orbit(centre, angle, ORBIT_RADIUS);
                Location mid = orbit(centre, (previous + angle) / 2, ORBIT_RADIUS);
                hitAround(caster, mid, lastHit, ticks);
                hitAround(caster, pos, lastHit, ticks);

                // Flames trail off the head of the axe.
                Location head = orbit(centre, angle, ORBIT_RADIUS + 0.9);
                World w = head.getWorld();
                w.spawnParticle(Particle.FLAME, head, 3, 0.15, 0.1, 0.15, 0.01);
                w.spawnParticle(Particle.FLAME, orbit(centre, (previous + angle) / 2, ORBIT_RADIUS + 0.9),
                        2, 0.1, 0.1, 0.1, 0.01);
                if (ticks % 3 == 0) {
                    w.spawnParticle(Particle.LAVA, head, 1, 0.2, 0.1, 0.2, 0.0);
                }
                if (ticks % 5 == 0) {
                    w.playSound(pos, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 0.6f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        active.put(caster.getUniqueId(), new Active(task, display));
    }

    public void stop(Player caster) {
        Active a = active.remove(caster.getUniqueId());
        if (a == null) return;
        a.task().cancel();
        if (a.display().isValid()) a.display().remove();
    }

    /** Removes every spinning axe; call from onDisable. */
    public void removeAll() {
        for (Active a : active.values()) {
            a.task().cancel();
            if (a.display().isValid()) a.display().remove();
        }
        active.clear();
    }

    /** Chest height on the caster, with yaw/pitch zeroed so the display never tilts with the camera. */
    private static Location anchor(Player caster) {
        Location loc = caster.getLocation();
        loc.setYaw(0f);
        loc.setPitch(0f);
        return loc.add(0, 1.0, 0);
    }

    private static Location orbit(Location centre, double angle, double radius) {
        return centre.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }

    /**
     * Axe lying flat, centre {@link #ORBIT_RADIUS} away from the caster at {@code angle}, rotated
     * so its head points away from the caster (the sprite's head is on its upper-right diagonal).
     */
    private static Transformation transform(double angle) {
        Vector3f offset = new Vector3f(
                (float) (Math.cos(angle) * ORBIT_RADIUS), 0f, (float) (Math.sin(angle) * ORBIT_RADIUS));

        // Flat sprite: head diagonal points at heading -45 degrees; rotateY(phi) lowers a heading by phi.
        float yaw = (float) -(angle + Math.toRadians(45 + FACING_OFFSET_DEGREES));
        Quaternionf rotation = new Quaternionf()
                .rotateY(yaw)
                .rotateX((float) Math.toRadians(-90));

        return new Transformation(offset, rotation,
                new Vector3f(AXE_SCALE, AXE_SCALE, AXE_SCALE), new Quaternionf());
    }

    private void hitAround(Player caster, Location at, Map<UUID, Integer> lastHit, int ticks) {
        for (LivingEntity entity : at.getNearbyLivingEntities(HIT_RADIUS, HIT_RADIUS, HIT_RADIUS)) {
            if (!Targets.isValidPlayer(caster, entity)) continue;

            Integer last = lastHit.get(entity.getUniqueId());
            if (last != null && ticks - last < HIT_INTERVAL_TICKS) continue;
            lastHit.put(entity.getUniqueId(), ticks);

            entity.damage(HIT_DAMAGE,
                    DamageSource.builder(DamageType.MAGIC)
                            .withCausingEntity(caster)
                            .withDirectEntity(caster)
                            .build());
            entity.setFireTicks(Math.max(entity.getFireTicks(), BURN_TICKS));
            entity.getWorld().spawnParticle(Particle.FLAME,
                    entity.getLocation().add(0, 1.0, 0), 12, 0.3, 0.5, 0.3, 0.03);
        }
    }
}
