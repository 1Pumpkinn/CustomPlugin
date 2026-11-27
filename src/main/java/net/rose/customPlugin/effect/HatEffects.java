package net.rose.customPlugin.effect;

import net.rose.customPlugin.item.misc.Hat;
import net.rose.customPlugin.util.DeathMessages;
import net.rose.customPlugin.util.Targets;
import net.rose.customPlugin.util.TrueDamage;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The two ways to throw the Hat. Both are an item display carrying the hat model:
 * <ul>
 *   <li>Bounce hat: arcs out, lands on a top face and stays for 2.5s, launching whatever steps on it like a slime block.</li>
 *   <li>Razor hat: flies straight; if it hits a player or mob it sticks to their head, spinning and cutting them.</li>
 * </ul>
 */
public final class HatEffects {

    // --- Bounce hat ---
    private static final double PAD_SPEED = 0.9;
    private static final double PAD_GRAVITY = 0.035;
    private static final int PAD_MAX_FLIGHT_TICKS = 80;
    private static final int PAD_LIFETIME_TICKS = 50;       // 2.5s once landed
    private static final float PAD_SCALE = 1.4f;
    private static final double PAD_HALF_WIDTH = 0.5;
    private static final double PAD_ZONE_HEIGHT = 0.45;     // feet inside this much above the floor count as "on it"
    private static final double BOUNCE_MIN = 1.05;          // launch velocity, blocks/tick
    private static final double BOUNCE_MAX = 1.8;
    private static final int BOUNCE_COOLDOWN_TICKS = 10;

    // --- Razor hat ---
    private static final double RAZOR_SPEED = 1.3;
    private static final double RAZOR_GRAVITY = 0.01;
    private static final int RAZOR_MAX_FLIGHT_TICKS = 25;
    private static final double RAZOR_RAY_SIZE = 0.4;
    private static final int RAZOR_STICK_TICKS = 80;        // 4s on the victim
    private static final int RAZOR_HIT_INTERVAL = 10;       // a cut every half second
    private static final double RAZOR_DAMAGE = 1.0;         // per cut, ignores armor
    private static final float RAZOR_SCALE = 1.3f;

    /** The hat model's center relative to the display's origin (the model is built around 8,8,8; the hat sits at y 0-4). */
    private static final Vector3f HAT_CENTER = new Vector3f(0f, -0.375f, 0f);

    private final JavaPlugin plugin;
    private final Hat hat;
    private final DeathMessages deaths;

    private final Set<Job> jobs = new HashSet<>();
    private final Set<UUID> thrown = new HashSet<>();
    private final Map<UUID, Integer> lastBounce = new HashMap<>();

    public HatEffects(JavaPlugin plugin, Hat hat, DeathMessages deaths) {
        this.plugin = plugin;
        this.hat = hat;
        this.deaths = deaths;
    }

    /** True while one of this player's thrown hats is still out (so a second one is not thrown at the same time). */
    public boolean hasThrown(Player player) {
        return thrown.contains(player.getUniqueId());
    }

    /** Ends this player's thrown hats right away. */
    public void cancel(Player player) {
        for (Job job : new ArrayList<>(jobs)) {
            if (job.owner.getUniqueId().equals(player.getUniqueId())) job.stop();
        }
    }

    /** Ends every thrown hat; call from onDisable. */
    public void removeAll() {
        for (Job job : new ArrayList<>(jobs)) job.stop();
        lastBounce.clear();
    }

    // ------------------------------------------------------------------ Throws

    public void throwPad(Player owner) {
        Location eye = owner.getEyeLocation();
        Vector direction = eye.getDirection();
        Location start = eye.clone().add(direction.clone().multiply(0.6)).subtract(0, 0.2, 0);
        Vector velocity = direction.clone().multiply(PAD_SPEED).add(new Vector(0, 0.12, 0));
        start.getWorld().playSound(start, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f);
        new PadJob(owner, spawnDisplay(start), start, velocity).begin();
    }

    /**
     * @param onMiss runs if the hat ends without hitting anyone
     */
    public void throwRazor(Player owner, Runnable onMiss) {
        Location eye = owner.getEyeLocation();
        Vector direction = eye.getDirection();
        Location start = eye.clone().add(direction.clone().multiply(0.6)).subtract(0, 0.2, 0);
        Vector velocity = direction.clone().multiply(RAZOR_SPEED);
        start.getWorld().playSound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.4f);
        new RazorJob(owner, spawnDisplay(start), start, velocity, onMiss).begin();
    }

    // ----------------------------------------------------------------- Display

    private ItemDisplay spawnDisplay(Location at) {
        return at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(hat.create());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setPersistent(false);
            display.setInterpolationDuration(1);
            display.setTeleportDuration(1);
        });
    }

    /**
     * Rotates and scales the hat about its own center, then places that center {@code centerAt} away from the
     * display's origin (null = on the origin). Interpolates over the next tick.
     */
    public static void pose(ItemDisplay display, Quaternionf rotation, Vector3f scale, Vector3f centerAt) {
        Vector3f offset = new Vector3f(HAT_CENTER).mul(scale).rotate(rotation).negate();
        if (centerAt != null) offset.add(centerAt);
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(offset, rotation, scale, new Quaternionf()));
    }

    // -------------------------------------------------------------------- Jobs

    private abstract class Job extends BukkitRunnable {
        final Player owner;
        final ItemDisplay display;

        Job(Player owner, ItemDisplay display) {
            this.owner = owner;
            this.display = display;
        }

        void begin() {
            jobs.add(this);
            thrown.add(owner.getUniqueId());
            runTaskTimer(plugin, 0L, 1L);
        }

        /** @return false once the hat is done and should be removed */
        abstract boolean step();

        @Override
        public void run() {
            // A display that is gone (its chunk unloaded, say) ends the job instead of ticking on a dead entity.
            if (!display.isValid() || !step()) stop();
        }

        void stop() {
            try {
                cancel();
            } catch (IllegalStateException ignored) {
                // never scheduled
            }
            display.remove();
            jobs.remove(this);
            thrown.remove(owner.getUniqueId());
        }
    }

    private final class PadJob extends Job {
        private final World world;
        private final Location pos;
        private final Vector velocity;

        private int ticks = 0;
        private float spin = 0f;

        private boolean landed = false;
        private double landX;
        private double landY;
        private double landZ;
        private int restTicks = 0;

        PadJob(Player owner, ItemDisplay display, Location start, Vector velocity) {
            super(owner, display);
            this.world = start.getWorld();
            this.pos = start.clone();
            this.velocity = velocity;
        }

        @Override
        boolean step() {
            return landed ? rest() : fly();
        }

        private boolean fly() {
            if (++ticks > PAD_MAX_FLIGHT_TICKS) return false;

            velocity.setY(velocity.getY() - PAD_GRAVITY);
            double distance = velocity.length();
            RayTraceResult hit = distance > 1e-6
                    ? world.rayTraceBlocks(pos, velocity.clone().normalize(), distance, FluidCollisionMode.NEVER, true)
                    : null;

            if (hit != null && hit.getHitBlockFace() != null) {
                Vector at = hit.getHitPosition();
                BlockFace face = hit.getHitBlockFace();
                if (face == BlockFace.UP) {
                    landed = true;
                    landX = at.getX();
                    landY = at.getY();
                    landZ = at.getZ();
                    pose(display, new Quaternionf().rotationY((float) Math.toRadians(spin)),
                            new Vector3f(PAD_SCALE), null);
                    display.teleport(new Location(world, landX, landY + 0.125 * PAD_SCALE, landZ));
                    world.playSound(at.toLocation(world), Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 1.2f);
                    return true;
                }
                // Wall or ceiling: bounce off it and keep falling until a floor turns up.
                Vector normal = face.getDirection();
                pos.set(at.getX() + normal.getX() * 0.1, at.getY() + normal.getY() * 0.1, at.getZ() + normal.getZ() * 0.1);
                velocity.subtract(normal.clone().multiply(2 * velocity.dot(normal))).multiply(0.4);
                world.playSound(pos, Sound.BLOCK_SLIME_BLOCK_HIT, 0.6f, 1.2f);
            } else {
                pos.add(velocity);
            }
            if (pos.getY() < world.getMinHeight()) return false;

            spin += 40f;
            pose(display, new Quaternionf().rotationY((float) Math.toRadians(spin)),
                    new Vector3f(PAD_SCALE), null);
            display.teleport(pos);
            return true;
        }

        private boolean rest() {
            if (++restTicks > PAD_LIFETIME_TICKS) {
                int now = Bukkit.getCurrentTick();
                lastBounce.values().removeIf(tick -> now - tick > 40);
                world.playSound(new Location(world, landX, landY, landZ), Sound.BLOCK_SLIME_BLOCK_BREAK, 1f, 1.2f);
                world.spawnParticle(Particle.ITEM_SLIME, landX, landY + 0.2, landZ, 12, 0.3, 0.1, 0.3, 0.0);
                return false;
            }

            bounceEntities(); // the hat itself stays perfectly still while it rests
            return true;
        }

        /** Launches every living thing standing on or falling onto the hat, like a slime block. Crouching skips it. */
        private void bounceEntities() {
            int now = Bukkit.getCurrentTick();
            lastBounce.values().removeIf(tick -> now - tick > 40);

            BoundingBox zone = new BoundingBox(
                    landX - PAD_HALF_WIDTH, landY - 0.1, landZ - PAD_HALF_WIDTH,
                    landX + PAD_HALF_WIDTH, landY + PAD_ZONE_HEIGHT, landZ + PAD_HALF_WIDTH);

            for (Entity entity : world.getNearbyEntities(zone, e -> e instanceof LivingEntity && !(e instanceof ArmorStand))) {
                LivingEntity living = (LivingEntity) entity;
                if (living instanceof Player player
                        && (player.isSneaking() || player.getGameMode() == GameMode.SPECTATOR)) continue;

                Integer last = lastBounce.get(living.getUniqueId());
                if (last != null && now - last < BOUNCE_COOLDOWN_TICKS) continue;
                lastBounce.put(living.getUniqueId(), now);

                // Harder landings bounce higher.
                double strength = Math.max(BOUNCE_MIN, Math.min(BOUNCE_MAX, BOUNCE_MIN + living.getFallDistance() * 0.06));
                Vector velocity = living.getVelocity();
                velocity.setY(strength);
                living.setVelocity(velocity);
                living.setFallDistance(0f);

                Location feet = living.getLocation();
                world.playSound(feet, Sound.ENTITY_SLIME_JUMP, 1f, 1.1f);
                world.spawnParticle(Particle.ITEM_SLIME, feet.clone().add(0, 0.1, 0), 8, 0.3, 0.05, 0.3, 0.0);
            }
        }
    }

    private final class RazorJob extends Job {
        private final World world;
        private final Location pos;
        private final Vector velocity;
        private final Runnable onMiss;

        private int ticks = 0;
        private float spin = 0f;

        private LivingEntity victim = null;
        private int stuckTicks = 0;

        RazorJob(Player owner, ItemDisplay display, Location start, Vector velocity, Runnable onMiss) {
            super(owner, display);
            this.world = start.getWorld();
            this.pos = start.clone();
            this.velocity = velocity;
            this.onMiss = onMiss;
        }

        @Override
        boolean step() {
            return victim == null ? fly() : stick();
        }

        private boolean fly() {
            if (++ticks > RAZOR_MAX_FLIGHT_TICKS) return miss();

            velocity.setY(velocity.getY() - RAZOR_GRAVITY);
            double distance = velocity.length();
            RayTraceResult hit = world.rayTrace(pos, velocity.clone().normalize(), distance,
                    FluidCollisionMode.NEVER, true, RAZOR_RAY_SIZE, e -> Targets.isValidTarget(owner, e));

            if (hit != null) {
                if (hit.getHitEntity() instanceof LivingEntity living) {
                    victim = living;
                    stuckTicks = 0;
                    world.playSound(living.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1.6f);
                    return true;
                }
                Location at = hit.getHitPosition().toLocation(world);
                world.playSound(at, Sound.ITEM_SHIELD_BLOCK, 1f, 1.4f);
                world.spawnParticle(Particle.CRIT, at, 10, 0.1, 0.1, 0.1, 0.2);
                return miss();
            }

            pos.add(velocity);
            spin += 70f;
            pose(display, new Quaternionf().rotationY((float) Math.toRadians(spin)),
                    new Vector3f(RAZOR_SCALE), null);
            display.teleport(pos);
            world.spawnParticle(Particle.CRIT, pos, 1, 0.05, 0.05, 0.05, 0.0);
            return true;
        }

        private boolean miss() {
            if (owner.isOnline() && onMiss != null) onMiss.run();
            return false;
        }

        private boolean stick() {
            if (!owner.isOnline() || Targets.isGone(victim) || ++stuckTicks > RAZOR_STICK_TICKS) return false;

            spin += 55f;
            Location head = victim.getLocation().add(0, victim.getHeight() + 0.125 * RAZOR_SCALE, 0);
            pose(display, new Quaternionf().rotationY((float) Math.toRadians(spin)),
                    new Vector3f(RAZOR_SCALE), null);
            display.teleport(head);

            if ((stuckTicks - 1) % RAZOR_HIT_INTERVAL == 0) {
                deaths.mark(victim, owner, DeathMessages.HAT);
                TrueDamage.of(RAZOR_DAMAGE).attacker(owner).apply(victim);
                world.playSound(head, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.6f);
                world.spawnParticle(Particle.CRIT, head, 8, 0.3, 0.2, 0.3, 0.2);
            }
            return true;
        }
    }
}
