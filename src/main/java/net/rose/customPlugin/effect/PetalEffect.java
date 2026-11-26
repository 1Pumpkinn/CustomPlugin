package net.rose.customPlugin.effect;

import net.rose.customPlugin.util.DeathMessages;
import net.rose.customPlugin.util.Targets;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Petal Blade's petals: a ring of pink petals that circles the owner, and the throw that fires one
 * of them at whatever the owner is looking at. A petal that connects deals one heart of magic damage.
 */
public final class PetalEffect {

    public static final int PETAL_COUNT = 3;
    public static final int LIFETIME_TICKS = 600;           // unthrown petals fade after 30s

    private static final double ORBIT_RADIUS = 1.5;
    private static final double ORBIT_STEP = 0.22;          // radians per tick
    private static final double ORBIT_HEIGHT = 1.0;
    private static final float PETAL_SCALE = 0.6f;

    private static final double PETAL_DAMAGE = 2.0;         // 1 heart
    private static final double THROW_SPEED = 1.6;          // blocks per tick
    private static final int THROW_MAX_TICKS = 20;
    private static final double AIM_RANGE = 40.0;
    private static final double AIM_RAY_SIZE = 0.5;
    private static final double HIT_MARGIN = 0.3;
    private static final int THROW_DELAY_TICKS = 4;

    private static final Particle.DustOptions PINK_DUST = new Particle.DustOptions(Color.fromRGB(255, 143, 200), 1.1f);

    /** What the owner's listener needs to hear about. */
    public interface Callbacks {
        /** A petal was thrown; {@code remaining} are still circling. */
        void onPetalsChanged(Player owner, int remaining);

        /** The swarm is over: every petal was thrown, or they faded. Not called when cancelled. */
        void onSwarmEnded(Player owner);
    }

    private static final class Petal {
        private final ItemDisplay display;
        private final double offset;

        private Petal(ItemDisplay display, double offset) {
            this.display = display;
            this.offset = offset;
        }
    }

    private final class Swarm {
        private final Player owner;
        private final Callbacks callbacks;
        private final List<Petal> petals = new ArrayList<>();
        private BukkitTask task;
        private int age;
        private int lastThrow = -THROW_DELAY_TICKS;

        private Swarm(Player owner, Callbacks callbacks) {
            this.owner = owner;
            this.callbacks = callbacks;
        }
    }

    private final JavaPlugin plugin;
    private final DeathMessages deaths;
    private final Map<UUID, Swarm> swarms = new HashMap<>();
    private final Set<ItemDisplay> flying = new HashSet<>();

    public PetalEffect(JavaPlugin plugin, DeathMessages deaths) {
        this.plugin = plugin;
        this.deaths = deaths;
    }

    public boolean isActive(Player owner) {
        return swarms.containsKey(owner.getUniqueId());
    }

    /** Spawns the petals around the owner. Does nothing if they already have a swarm. */
    public void summon(Player owner, Callbacks callbacks) {
        if (isActive(owner)) return;

        Swarm swarm = new Swarm(owner, callbacks);
        for (int i = 0; i < PETAL_COUNT; i++) {
            double offset = Math.PI * 2 * i / PETAL_COUNT;
            swarm.petals.add(new Petal(spawnDisplay(orbitPoint(owner, offset, 0)), offset));
        }
        swarms.put(owner.getUniqueId(), swarm);

        World world = owner.getWorld();
        world.playSound(owner.getLocation(), Sound.BLOCK_CHERRY_LEAVES_PLACE, SoundCategory.PLAYERS, 1.2f, 1.2f);
        world.playSound(owner.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.8f);

        swarm.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!owner.isOnline() || owner.isDead() || swarm.age >= LIFETIME_TICKS) {
                    boolean faded = owner.isOnline() && !owner.isDead();
                    finish(swarm, faded);
                    return;
                }
                orbit(swarm);
                swarm.age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Throws one petal at what the owner is looking at. Returns false if they have no petals out. */
    public boolean throwPetal(Player owner) {
        Swarm swarm = swarms.get(owner.getUniqueId());
        if (swarm == null || swarm.petals.isEmpty()) return false;
        if (swarm.age - swarm.lastThrow < THROW_DELAY_TICKS) return true;
        swarm.lastThrow = swarm.age;

        Location eye = owner.getEyeLocation();
        Vector look = eye.getDirection();

        // Throw the petal that is currently closest to where the owner is looking.
        Petal best = swarm.petals.get(0);
        double bestDot = -2;
        for (Petal petal : swarm.petals) {
            Vector toPetal = petal.display.getLocation().toVector().subtract(eye.toVector());
            if (toPetal.lengthSquared() < 1.0E-6) continue;
            double dot = toPetal.normalize().dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = petal;
            }
        }
        swarm.petals.remove(best);

        Location start = best.display.getLocation();
        Vector velocity = aimPoint(owner, eye, look).subtract(start.toVector());
        if (velocity.lengthSquared() < 1.0) velocity = look.clone();
        velocity.normalize().multiply(THROW_SPEED);

        launch(owner, best.display, velocity);
        owner.getWorld().playSound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.6f, 1.7f);
        owner.getWorld().playSound(start, Sound.BLOCK_CHERRY_LEAVES_HIT, SoundCategory.PLAYERS, 1.0f, 1.4f);

        swarm.callbacks.onPetalsChanged(owner, swarm.petals.size());
        if (swarm.petals.isEmpty()) finish(swarm, true);
        return true;
    }

    /** Removes the owner's circling petals without ending the ability (death, quit). Petals already thrown keep flying. */
    public void cancel(Player owner) {
        Swarm swarm = swarms.get(owner.getUniqueId());
        if (swarm != null) finish(swarm, false);
    }

    /** Removes every petal; call from onDisable. */
    public void removeAll() {
        for (Swarm swarm : new ArrayList<>(swarms.values())) {
            finish(swarm, false);
        }
        for (ItemDisplay display : new ArrayList<>(flying)) {
            display.remove();
        }
        flying.clear();
    }

    private void finish(Swarm swarm, boolean notify) {
        if (swarms.remove(swarm.owner.getUniqueId(), swarm)) {
            swarm.task.cancel();
            for (Petal petal : swarm.petals) {
                Location at = petal.display.getLocation();
                at.getWorld().spawnParticle(Particle.CHERRY_LEAVES, at, 6, 0.15, 0.15, 0.15, 0.02);
                petal.display.remove();
            }
            swarm.petals.clear();
            if (notify) swarm.callbacks.onSwarmEnded(swarm.owner);
        }
    }

    private void orbit(Swarm swarm) {
        Player owner = swarm.owner;
        double spin = swarm.age * ORBIT_STEP;
        for (Petal petal : swarm.petals) {
            Location at = orbitPoint(owner, petal.offset + spin, swarm.age);
            petal.display.teleport(at);
            if (swarm.age % 3 == 0) {
                at.getWorld().spawnParticle(Particle.DUST, at, 1, 0.05, 0.05, 0.05, 0.0, PINK_DUST);
            }
        }
        if (swarm.age % 10 == 0 && !swarm.petals.isEmpty()) {
            Location any = swarm.petals.get(0).display.getLocation();
            any.getWorld().spawnParticle(Particle.CHERRY_LEAVES, any, 1, 0.2, 0.2, 0.2, 0.0);
        }
    }

    private static Location orbitPoint(Player owner, double angle, int age) {
        Location base = owner.getLocation().add(0, ORBIT_HEIGHT + Math.sin(age * 0.12 + angle) * 0.12, 0);
        return base.add(Math.cos(angle) * ORBIT_RADIUS, 0, Math.sin(angle) * ORBIT_RADIUS);
    }

    private ItemDisplay spawnDisplay(Location at) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.CHERRY_LEAVES));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(2);
            d.setPersistent(false);
            d.setTransformation(new Transformation(
                    new Vector3f(), new AxisAngle4f(), new Vector3f(PETAL_SCALE), new AxisAngle4f()));
        });
    }

    /** Where the crosshair points: the first valid target or block in range, or a point at full range. */
    private static Vector aimPoint(Player owner, Location eye, Vector look) {
        World world = eye.getWorld();
        RayTraceResult blocks = world.rayTraceBlocks(eye, look, AIM_RANGE, FluidCollisionMode.NEVER, true);
        double reach = blocks == null ? AIM_RANGE : blocks.getHitPosition().distance(eye.toVector());

        RayTraceResult entity = world.rayTraceEntities(eye, look, reach, AIM_RAY_SIZE,
                e -> Targets.isValidTarget(owner, e));
        if (entity != null && entity.getHitEntity() instanceof LivingEntity target) {
            return target.getLocation().toVector().add(new Vector(0, target.getHeight() * 0.55, 0));
        }
        if (blocks != null) return blocks.getHitPosition();
        return eye.toVector().add(look.clone().multiply(AIM_RANGE));
    }

    private void launch(Player owner, ItemDisplay display, Vector velocity) {
        flying.add(display);
        display.setTeleportDuration(1);

        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (!display.isValid() || ticks++ >= THROW_MAX_TICKS) {
                    end(display, null);
                    cancel();
                    return;
                }

                Location from = display.getLocation();
                World world = from.getWorld();
                double length = velocity.length();
                Vector direction = velocity.clone().multiply(1.0 / length);

                RayTraceResult wall = world.rayTraceBlocks(from, direction, length, FluidCollisionMode.NEVER, true);
                double reach = wall == null ? length : wall.getHitPosition().distance(from.toVector());

                LivingEntity victim = firstHit(owner, from, direction, reach);
                if (victim != null) {
                    hit(owner, victim);
                    end(display, victim.getLocation().add(0, victim.getHeight() * 0.55, 0));
                    cancel();
                    return;
                }
                if (wall != null) {
                    end(display, wall.getHitPosition().toLocation(world));
                    cancel();
                    return;
                }

                Location next = from.clone().add(velocity);
                display.teleport(next);
                world.spawnParticle(Particle.DUST, next, 2, 0.05, 0.05, 0.05, 0.0, PINK_DUST);
                if (ticks % 3 == 0) {
                    world.spawnParticle(Particle.CHERRY_LEAVES, next, 1, 0.1, 0.1, 0.1, 0.0);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Nearest valid target the petal passes through this tick, or null. */
    private static LivingEntity firstHit(Player owner, Location from, Vector direction, double reach) {
        Vector origin = from.toVector();
        Location middle = from.clone().add(direction.clone().multiply(reach / 2));
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : from.getWorld().getNearbyLivingEntities(middle, reach / 2 + 2.0)) {
            if (!Targets.isValidTarget(owner, candidate)) continue;

            BoundingBox box = candidate.getBoundingBox().expand(HIT_MARGIN);
            double distance;
            if (box.contains(origin)) {
                distance = 0;
            } else {
                RayTraceResult ray = box.rayTrace(origin, direction, reach);
                if (ray == null) continue;
                distance = ray.getHitPosition().distance(origin);
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private void hit(Player owner, LivingEntity victim) {
        victim.setNoDamageTicks(0); // each petal lands even right after another one
        deaths.mark(victim, owner, DeathMessages.PETAL_BLADE);
        victim.damage(PETAL_DAMAGE, DamageSource.builder(DamageType.MAGIC)
                .withCausingEntity(owner)
                .withDirectEntity(owner)
                .build());
    }

    private void end(ItemDisplay display, Location burst) {
        flying.remove(display);
        Location at = burst != null ? burst : display.isValid() ? display.getLocation() : null;
        if (at != null) {
            World world = at.getWorld();
            world.spawnParticle(Particle.CHERRY_LEAVES, at, burst != null ? 16 : 6, 0.25, 0.25, 0.25, 0.04);
            if (burst != null) {
                world.spawnParticle(Particle.DUST, at, 10, 0.25, 0.25, 0.25, 0.0, PINK_DUST);
                world.playSound(at, Sound.BLOCK_CHERRY_LEAVES_BREAK, SoundCategory.PLAYERS, 1.0f, 1.3f);
            }
        }
        if (display.isValid()) display.remove();
    }
}
