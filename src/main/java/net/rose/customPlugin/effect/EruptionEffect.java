package net.rose.customPlugin.effect;

import net.rose.customPlugin.util.Targets;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lava eruption: glowing cracks warn for a moment, then a fountain of lava and basalt slabs
 * bursts out of the ground, damaging, burning and launching every player inside the blast.
 */
public final class EruptionEffect {

    // --- Gameplay ---
    private static final int WARNING_TICKS = 6;
    private static final double RADIUS = 6.0;
    // Damage falls off linearly with distance from the centre: 2 hearts in the middle, 1 heart at the edge.
    private static final double CENTER_DAMAGE = 4.0;
    private static final double EDGE_DAMAGE = 2.0;
    private static final double CORE_RADIUS = 1.5;
    private static final double LAUNCH_VELOCITY = 1.0;
    private static final int BURN_TICKS = 80;

    // --- Visuals ---
    private static final int CRACKS = 9;
    private static final int RING_POINTS = (int) Math.round(RADIUS * 11);

    private static final BlockData MAGMA = Material.MAGMA_BLOCK.createBlockData();
    private static final BlockData BASALT = Material.BASALT.createBlockData();
    private static final BlockData SMOOTH_BASALT = Material.SMOOTH_BASALT.createBlockData();

    private static final Particle.DustOptions CORE_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 130, 25), 1.5f);
    private static final Particle.DustOptions EDGE_DUST =
            new Particle.DustOptions(Color.fromRGB(210, 45, 0), 1.0f);
    private static final Particle.DustOptions DROP_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 160, 40), 1.1f);
    private static final Particle.DustTransition CRACK_DUST =
            new Particle.DustTransition(Color.fromRGB(255, 150, 30), Color.fromRGB(120, 15, 0), 0.9f);
    private static final Particle.DustTransition RISING_DUST =
            new Particle.DustTransition(Color.fromRGB(255, 175, 45), Color.fromRGB(110, 20, 0), 1.4f);
    private static final Particle.DustTransition TIP_DUST =
            new Particle.DustTransition(Color.fromRGB(255, 200, 80), Color.fromRGB(200, 40, 0), 1.0f);

    private static final double COLUMN_HEIGHT = 3.2;
    private static final int RISE_TICKS = 4;
    private static final int ERUPTION_TICKS = 24;
    private static final double COLUMN_RADIUS = 0.55;
    private static final int DROPLETS = 28;
    private static final double GRAVITY = 0.07;

    private static final float SLAB_HEIGHT = 1.4f;
    private static final float SLAB_WIDTH = 0.55f;
    private static final float SLAB_DEPTH = 0.35f;
    private static final float SLAB_TILT = (float) Math.toRadians(14);
    private static final double SLAB_SPACING = 1.8;
    private static final double[] RING_FRACTIONS = {0.86, 0.60, 0.34};

    private final JavaPlugin plugin;
    private final Set<BlockDisplay> activeSlabs = new HashSet<>();

    public EruptionEffect(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** The point on the floor below {@code from}, a hair above the surface. */
    public static Location groundBelow(Location from) {
        World world = from.getWorld();
        RayTraceResult down = world.rayTraceBlocks(
                from.clone().add(0, 0.5, 0), new Vector(0, -1, 0), 12.0, FluidCollisionMode.NEVER, true);
        Location ground = down == null ? from.clone() : down.getHitPosition().toLocation(world);
        ground.add(0, 0.05, 0);
        ground.setYaw(0f);
        ground.setPitch(0f);
        return ground;
    }

    /** Removes any slabs still standing; call from onDisable. */
    public void removeAll() {
        for (BlockDisplay slab : new ArrayList<>(activeSlabs)) {
            if (slab.isValid()) slab.remove();
        }
        activeSlabs.clear();
    }

    // ---------------------------------------------------------------- Warning phase

    public void cast(Player caster, Location center) {
        World world = center.getWorld();
        world.playSound(center, Sound.BLOCK_LAVA_AMBIENT, 1.5f, 0.7f);
        world.playSound(center, Sound.BLOCK_LAVA_POP, 1.2f, 0.6f);

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double[] crackAngle = new double[CRACKS];
        double[] crackPhase = new double[CRACKS];
        double crackOffset = rng.nextDouble() * Math.PI * 2;
        for (int c = 0; c < CRACKS; c++) {
            crackAngle[c] = crackOffset + c * (Math.PI * 2 / CRACKS) + (rng.nextDouble() - 0.5) * 0.5;
            crackPhase[c] = rng.nextDouble() * Math.PI * 2;
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= WARNING_TICKS) {
                    erupt(caster, center);
                    cancel();
                    return;
                }

                double progress = (double) ticks / WARNING_TICKS;

                if (ticks % 2 == 0) {
                    // Glowing cracks fan out from the centre and creep towards the blast radius.
                    double reach = RADIUS * (0.3 + 0.8 * progress);
                    for (int c = 0; c < CRACKS; c++) {
                        double dirX = Math.cos(crackAngle[c]);
                        double dirZ = Math.sin(crackAngle[c]);
                        double perpX = -dirZ;
                        double perpZ = dirX;
                        for (double d = 0.25; d <= reach; d += 0.25) {
                            double wobble = Math.sin(d * 4.5 + crackPhase[c]) * 0.18 * Math.min(1.0, d);
                            Location p = center.clone().add(
                                    dirX * d + perpX * wobble, 0.08, dirZ * d + perpZ * wobble);
                            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, p, 1,
                                    0.02, 0.0, 0.02, 0.0, CRACK_DUST, true);
                        }
                        Location end = center.clone().add(dirX * reach, 0.12, dirZ * reach);
                        world.spawnParticle(Particle.SMALL_FLAME, end, 1, 0.03, 0.03, 0.03, 0.0, null, true);
                    }

                    // Thin ring marking the blast radius, tightening as the timer runs out.
                    double ringRadius = RADIUS * (1.0 - 0.2 * progress);
                    for (int i = 0; i < RING_POINTS; i++) {
                        double angle = i * (Math.PI * 2 / RING_POINTS);
                        Location p = center.clone().add(
                                Math.cos(angle) * ringRadius, 0.1, Math.sin(angle) * ringRadius);
                        world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0.0, EDGE_DUST, true);
                    }
                }

                // Heat rising out of the cracked ground.
                world.spawnParticle(Particle.ASH, center.clone().add(0, 0.3, 0), 3,
                        RADIUS * 0.5, 0.3, RADIUS * 0.5, 0.0, null, true);
                if (ticks % 3 == 0) {
                    world.spawnParticle(Particle.LAVA, center.clone().add(0, 0.2, 0), 2,
                            RADIUS * 0.5, 0.1, RADIUS * 0.5, 0.0, null, true);
                    world.spawnParticle(Particle.BLOCK, center.clone().add(0, 0.15, 0), 6,
                            RADIUS * 0.4, 0.05, RADIUS * 0.4, 0.0, MAGMA, true);
                }
                if (ticks % 5 == 0) {
                    world.playSound(center, Sound.BLOCK_LAVA_POP, 0.8f, 0.8f + (float) progress * 0.6f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---------------------------------------------------------------- Eruption

    private void erupt(Player caster, Location center) {
        List<Location> tips = spawnSpikeRing(center);
        spawnFountain(center, tips);

        // Damage follows the visuals: a round blast the size of the shockwave ring, as tall as the
        // fountain. Anyone who has stepped out of the particles and spikes is not hit.
        Location column = center.clone().add(0, COLUMN_HEIGHT / 2, 0);
        for (LivingEntity e : column.getNearbyLivingEntities(
                RADIUS + 1.0, COLUMN_HEIGHT / 2 + 1.0, RADIUS + 1.0)) {
            if (!Targets.isValidPlayer(caster, e)) continue;
            if (!insideBlast(center, e)) continue;

            double dx = e.getLocation().getX() - center.getX();
            double dz = e.getLocation().getZ() - center.getZ();
            double falloff = Math.min(1.0, Math.sqrt(dx * dx + dz * dz) / RADIUS);
            double damage = CENTER_DAMAGE + (EDGE_DAMAGE - CENTER_DAMAGE) * falloff;

            e.setNoDamageTicks(0); // armor-ignoring magic damage should always land at full strength
            e.damage(damage,
                    DamageSource.builder(DamageType.MAGIC)
                            .withCausingEntity(caster)
                            .withDirectEntity(caster)
                            .build());
            e.setFireTicks(BURN_TICKS);
            e.setVelocity(new Vector(0, LAUNCH_VELOCITY, 0));
        }
    }

    private static boolean insideBlast(Location center, LivingEntity entity) {
        Location at = entity.getLocation();
        double dx = at.getX() - center.getX();
        double dz = at.getZ() - center.getZ();
        if (dx * dx + dz * dz > RADIUS * RADIUS) return false;

        BoundingBox box = entity.getBoundingBox();
        return box.getMaxY() >= center.getY() && box.getMinY() <= center.getY() + COLUMN_HEIGHT;
    }

    // ---------------------------------------------------------------- Fountain

    private void spawnFountain(Location center, List<Location> tips) {
        World world = center.getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Droplet ballistics, rolled once so each droplet follows a clean arc.
        double[] angle = new double[DROPLETS];
        double[] horizontal = new double[DROPLETS];
        double[] vertical = new double[DROPLETS];
        int[] start = new int[DROPLETS];
        boolean[] landed = new boolean[DROPLETS];
        for (int i = 0; i < DROPLETS; i++) {
            angle[i] = rng.nextDouble() * Math.PI * 2;
            horizontal[i] = (0.05 + rng.nextDouble() * 0.08) * (RADIUS / 2.5);
            vertical[i] = 0.5 + rng.nextDouble() * 0.25;
            start[i] = rng.nextInt(4);
        }

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= ERUPTION_TICKS) {
                    cancel();
                    return;
                }

                // Shockwave: a bright ring racing out along the ground.
                if (t < 7) {
                    double ringRadius = RADIUS * (t + 1) / 7.0;
                    for (int i = 0; i < RING_POINTS; i++) {
                        double a = i * (Math.PI * 2 / RING_POINTS);
                        Location p = center.clone().add(
                                Math.cos(a) * ringRadius, 0.12, Math.sin(a) * ringRadius);
                        world.spawnParticle(Particle.DUST, p, 1, 0.04, 0.03, 0.04, 0.0, EDGE_DUST, true);
                        if (i % 5 == 0) {
                            world.spawnParticle(Particle.SMALL_FLAME, p, 1, 0, 0.03, 0, 0.01, null, true);
                        }
                    }

                    // Smoke only rises from the middle of the blast, not around the whole ring.
                    if (t > 1) {
                        for (int s = 0; s < 4; s++) {
                            double a = rng.nextDouble() * Math.PI * 2;
                            double r = rng.nextDouble() * CORE_RADIUS;
                            Location inner = center.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
                            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, inner, 0,
                                    0, 0.06, 0, 1.0, null, true);
                        }
                    }
                }

                // Core column: grows to full height, then thins out. Tapered, with a single helix.
                double rise = Math.min(1.0, (t + 1) / (double) RISE_TICKS);
                double height = COLUMN_HEIGHT * rise;
                double fade = t < 9 ? 1.0 : Math.max(0.0, 1.0 - (t - 9) / 6.0);
                if (fade > 0.0) {
                    for (double y = 0; y <= height; y += 0.35) {
                        double taper = 1.0 - 0.7 * (y / COLUMN_HEIGHT);
                        double r = COLUMN_RADIUS * taper;
                        Location level = center.clone().add(0, y, 0);

                        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, level,
                                Math.max(1, (int) Math.round(2 * fade)),
                                r * 0.4, 0.12, r * 0.4, 0.0, RISING_DUST, true);
                        if (fade > 0.4) {
                            world.spawnParticle(Particle.DUST, level, 1,
                                    r * 0.2, 0.08, r * 0.2, 0.0, CORE_DUST, true);
                        }

                        double a = t * 0.8 + y * 2.4;
                        Location s = level.clone().add(Math.cos(a) * r * 1.4, 0, Math.sin(a) * r * 1.4);
                        world.spawnParticle(Particle.DUST, s, 1, 0, 0, 0, 0.0, CORE_DUST, true);
                    }
                    if (t % 2 == 0) {
                        world.spawnParticle(Particle.FLAME, center.clone().add(0, height * 0.5, 0), 2,
                                COLUMN_RADIUS * 0.3, height * 0.4, COLUMN_RADIUS * 0.3, 0.03, null, true);
                    }
                }

                // Lava droplets flying out in parabolas, splashing where they land.
                for (int i = 0; i < DROPLETS; i++) {
                    if (landed[i]) continue;
                    for (int sub = 0; sub < 2; sub++) {
                        double tau = t - start[i] + sub * 0.5;
                        if (tau < 0) continue;
                        double y = 0.3 + vertical[i] * tau - 0.5 * GRAVITY * tau * tau;
                        double d = horizontal[i] * tau;
                        if (y <= 0.05) {
                            landed[i] = true;
                            Location splash = center.clone().add(
                                    Math.cos(angle[i]) * d, 0.1, Math.sin(angle[i]) * d);
                            world.spawnParticle(Particle.LAVA, splash, 1, 0.05, 0.0, 0.05, 0.0, null, true);
                            world.spawnParticle(Particle.SMALL_FLAME, splash, 2,
                                    0.08, 0.02, 0.08, 0.02, null, true);
                            break;
                        }
                        Location p = center.clone().add(Math.cos(angle[i]) * d, y, Math.sin(angle[i]) * d);
                        world.spawnParticle(Particle.DUST, p, 1, 0.02, 0.02, 0.02, 0.0, DROP_DUST, true);
                        if (sub == 0 && vertical[i] - GRAVITY * tau < 0 && (t + i) % 3 == 0) {
                            world.spawnParticle(Particle.FALLING_LAVA, p, 1, 0, 0, 0, 0.0, null, true);
                        }
                    }
                }

                // Glowing tip and a lava drip on every spike while it stands.
                if (t >= 1 && t <= 14) {
                    for (Location tip : tips) {
                        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, tip, 2,
                                0.08, 0.08, 0.08, 0.0, TIP_DUST, true);
                        if (t % 2 == 0) {
                            world.spawnParticle(Particle.SMALL_FLAME, tip, 1,
                                    0.05, 0.05, 0.05, 0.01, null, true);
                        }
                        if (t % 4 == 0) {
                            world.spawnParticle(Particle.DRIPPING_LAVA, tip.clone().add(0, -0.3, 0), 1,
                                    0.1, 0.0, 0.1, 0.0, null, true);
                        }
                    }
                }

                // Embers drifting up over the whole blast area.
                if (t < 16) {
                    world.spawnParticle(Particle.ASH, center.clone().add(0, 1.0, 0), 4,
                            RADIUS * 0.8, 1.0, RADIUS * 0.8, 0.0, null, true);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---------------------------------------------------------------- Basalt slabs

    /**
     * Concentric rings of leaning basalt slabs (plus a small cluster in the middle) so the whole
     * blast area is covered. Every slab is one {@link BlockDisplay}.
     *
     * @return the world position of every slab top, for the glow
     */
    private List<Location> spawnSpikeRing(Location center) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<Location> tips = new ArrayList<>();

        int ringIndex = 0;
        for (double fraction : RING_FRACTIONS) {
            double ringRadius = RADIUS * fraction;
            int count = Math.max(4, (int) Math.round(Math.PI * 2 * ringRadius / SLAB_SPACING));
            double offset = rng.nextDouble() * Math.PI * 2;
            for (int i = 0; i < count; i++) {
                double angle = offset + i * (Math.PI * 2 / count)
                        + (rng.nextDouble() - 0.5) * (0.5 / count * Math.PI);
                double dist = ringRadius + (rng.nextDouble() - 0.5) * 0.5;
                tips.add(spawnSlab(center,
                        (float) (Math.cos(angle) * dist), (float) (Math.sin(angle) * dist),
                        SLAB_WIDTH, SLAB_HEIGHT, SLAB_DEPTH, SLAB_TILT, (float) angle,
                        (i + ringIndex) % 3 == 0 ? SMOOTH_BASALT : BASALT));
            }
            ringIndex++;
        }

        // A small cluster around the fountain itself.
        int core = 4;
        double coreOffset = rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < core; i++) {
            double angle = coreOffset + i * (Math.PI * 2 / core) + (rng.nextDouble() - 0.5) * 0.6;
            double dist = RADIUS * 0.12 + rng.nextDouble() * 0.3;
            tips.add(spawnSlab(center,
                    (float) (Math.cos(angle) * dist), (float) (Math.sin(angle) * dist),
                    SLAB_WIDTH, SLAB_HEIGHT, SLAB_DEPTH, SLAB_TILT, (float) angle, BASALT));
        }
        return tips;
    }

    /**
     * One slab = one block display scaled to {@code width x height x depth} and leaned about the
     * middle of its base. It shoots up out of the floor, settles, holds, then sinks back in.
     *
     * @param tilt    lean angle in radians (0 = straight up)
     * @param leanDir horizontal direction the slab leans towards, in radians
     * @return world position of the top of the slab
     */
    private Location spawnSlab(Location center, float dx, float dz, float width, float height,
                               float depth, float tilt, float leanDir, BlockData block) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        World world = center.getWorld();

        // Lean away from the centre, then a spin about the slab's own axis so the faces differ.
        Quaternionf rotation = new Quaternionf();
        if (tilt != 0f) {
            rotation.rotateAxis(tilt, (float) Math.sin(leanDir), 0f, (float) -Math.cos(leanDir));
        }
        rotation.rotateY((float) (rng.nextDouble() * Math.PI * 2));

        Vector3f base = new Vector3f(dx, -0.2f, dz);

        Transformation hidden = slabTransform(base, rotation, 0.05f, 0.05f, 0.05f);
        Transformation overshoot = slabTransform(base, rotation, width, height * 1.12f, depth);
        Transformation grown = slabTransform(base, rotation, width, height, depth);

        BlockDisplay slab = world.spawn(center, BlockDisplay.class, d -> {
            d.setBlock(block);
            d.setBrightness(new Display.Brightness(10, 12));
            d.setTransformation(hidden);
            d.setInterpolationDuration(2);
            d.setShadowRadius(0f);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
        activeSlabs.add(slab);

        long delay = 1L + rng.nextInt(3);

        // Burst out past full height, settle back, hold, sink away, remove.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!slab.isValid()) return;
            slab.setInterpolationDelay(0);
            slab.setInterpolationDuration(2);
            slab.setTransformation(overshoot);
        }, delay);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!slab.isValid()) return;
            slab.setInterpolationDelay(0);
            slab.setInterpolationDuration(3);
            slab.setTransformation(grown);
        }, delay + 2);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!slab.isValid()) return;
            slab.setInterpolationDelay(0);
            slab.setInterpolationDuration(6);
            slab.setTransformation(hidden);
        }, 14L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (slab.isValid()) slab.remove();
            activeSlabs.remove(slab);
        }, 21L);

        Vector3f top = rotation.transform(new Vector3f(0f, height, 0f));
        return center.clone().add(base.x + top.x, base.y + top.y, base.z + top.z);
    }

    private static Transformation slabTransform(Vector3f base, Quaternionf rotation,
                                                float width, float height, float depth) {
        Vector3f baseCentre = rotation.transform(new Vector3f(0.5f * width, 0f, 0.5f * depth));
        return new Transformation(
                new Vector3f(base).sub(baseCentre),
                new Quaternionf(rotation),
                new Vector3f(width, height, depth),
                new Quaternionf());
    }
}
