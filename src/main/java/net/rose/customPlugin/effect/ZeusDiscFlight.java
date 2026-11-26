package net.rose.customPlugin.effect;

import net.rose.customPlugin.util.DiscPathfinder;
import net.rose.customPlugin.util.Targets;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * One throw of the disc: out in a straight line, ricochet off walls, then fly home to the thrower,
 * around corners if it has to.
 * Ported from the ElementSMPRefined throwing disc (same numbers).
 * <p>
 * Bukkit has no custom entities, so the disc is an {@link ItemDisplay} that this class moves every
 * tick. While it flies the real item is not in anyone's inventory; it is held here (the flight) and is always handed back or dropped when the flight ends, whatever the reason.
 */
public final class ZeusDiscFlight {
    private static final double WIDTH = 0.6;
    private static final double THROW_SPEED = 1.6;
    private static final double SPAWN_FORWARD = 0.5;
    /** Blocks the disc flies on the way out before it turns around. */
    private static final double BASE_RANGE = 6.0;
    /** Extra blocks of range added on every ricochet (stacks with whatever range is left). */
    private static final double RICOCHET_RANGE_BONUS = 5.0;
    private static final int MAX_RICOCHETS = 50;
    /** Safety net: after this many ticks in the air the disc stops bouncing and comes home. */
    private static final int MAX_OUTBOUND_TICKS = 120;
    private static final double BOUNCE_SPEED_RETAIN = 0.6;
    private static final double MIN_BOUNCE_SPEED = 0.7;
    private static final int BOUNCE_COOLDOWN_TICKS = 3;
    private static final double RETURN_SPEED = 1.5;
    private static final double RETURN_TURN_RATE = 0.35;
    private static final double CATCH_DISTANCE = 1.4;
    private static final double HIT_MARGIN = 0.3;
    private static final int HIT_COOLDOWN_TICKS = 10;
    /** Hard backstop: max ticks spent flying home (60s). Time spent on the way out never counts towards it. */
    private static final int MAX_LIFETIME_TICKS = 1200;
    /** Damage of the disc itself, in half hearts. */
    private static final double DISC_DAMAGE = 6.0;
    private static final double KNOCKBACK = 0.4;
    private static final double FLIGHT_DIR_SMOOTHING = 0.5;
    /** Ticks without real movement on the way home before the disc is snapped back to a free cell. */
    private static final int STUCK_TICKS = 5;
    private static final double STUCK_EPSILON_SQ = 0.02 * 0.02;
    /** How often (ticks) the return path is re-planned while the owner isn't in line of sight. */
    private static final int REPATH_INTERVAL = 8;
    /** Re-plan early if the owner moved this far (squared blocks) from where the path was aimed. */
    private static final double REPATH_GOAL_DRIFT_SQ = 4.0;
    /** How many waypoints ahead the disc looks when cutting corners. */
    private static final int PATH_LOOKAHEAD = 8;
    private static final double WAYPOINT_REACHED_SQ = 0.5 * 0.5;
    /**
     * If the disc makes no progress towards the owner for this long (10s) it gives up and drops on the
     * ground where it is. A disc that is still actively getting closer never drops, however long the detour.
     */
    private static final int NO_PROGRESS_DROP_TICKS = 200;
    /** The remaining route has to shrink by at least this much (blocks) to count as progress. */
    private static final double PROGRESS_EPSILON = 0.25;

    private static final float SPIN_RADIANS_PER_TICK = (float) Math.toRadians(70.0);
    private static final double MAX_FLIGHT_PITCH = 75.0;
    /** The sprite is 14 of its 16 pixels wide, so scale it up until the disc is {@link #WIDTH}. */
    private static final float SCALE = (float) (WIDTH / (14.0 / 16.0));

    /** Blocks the disc never bounces off (it just stops and comes back). */
    private static final Set<Material> NO_RICOCHET =
            Set.of(
                    Material.SLIME_BLOCK,
                    Material.HONEY_BLOCK);

    private final JavaPlugin plugin;
    private final ZeusLightning lightning;
    private final Consumer<ZeusDiscFlight> onEnd;
    private final UUID ownerId;
    private final World world;
    private final int homeSlot;
    private final boolean primed;
    private final Map<UUID, Integer> lastHitTick = new HashMap<>();
    /** Entities already struck by lightning on this throw (a primed throw strikes each one once). */
    private final Set<UUID> struck = new HashSet<>();

    private ItemStack stack;
    private ItemDisplay display;
    private BukkitTask task;

    private Vector pos;
    private Vector velocity;
    private Vector flightDir;
    private float spin;

    private boolean returning;
    private boolean finished;
    private int age;
    private int returnTicks;
    private int ricochets;
    private int lastBounceAge = -1000;
    private double rangeLeft = BASE_RANGE;
    private Vector heading = new Vector();
    private Vector pathGoal = new Vector();
    private List<Vector> path = List.of();
    private int pathIndex;
    private int ticksSinceRepath;
    private int noProgressTicks;
    private double bestRemaining = Double.MAX_VALUE;
    private int stuckTicks;

    private ZeusDiscFlight(
            JavaPlugin plugin,
            ZeusLightning lightning,
            Consumer<ZeusDiscFlight> onEnd,
            Player owner,
            ItemStack stack,
            int homeSlot,
            boolean primed) {
        this.plugin = plugin;
        this.lightning = lightning;
        this.onEnd = onEnd;
        this.ownerId = owner.getUniqueId();
        this.stack = stack;
        this.homeSlot = homeSlot;
        this.primed = primed;
        this.world = owner.getWorld();
    }

    public static ZeusDiscFlight launch(
            JavaPlugin plugin,
            ZeusLightning lightning,
            Consumer<ZeusDiscFlight> onEnd,
            Player owner,
            ItemStack stack,
            int homeSlot,
            boolean primed) {
        ZeusDiscFlight disc = new ZeusDiscFlight(plugin, lightning, onEnd, owner, stack, homeSlot, primed);
        disc.start(owner);
        return disc;
    }

    public UUID ownerId() {
        return ownerId;
    }

    private void start(Player owner) {
        Location eye = owner.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Vector start = eye.toVector().add(look.clone().multiply(SPAWN_FORWARD));

        RayTraceResult obstruction =
                world.rayTraceBlocks(eye, look, SPAWN_FORWARD, FluidCollisionMode.NEVER, true);
        if (obstruction != null && obstruction.getHitBlock() != null) {
            start = obstruction.getHitPosition().subtract(look.clone().multiply(0.05));
        }

        this.pos = start;
        this.velocity = look.clone().multiply(THROW_SPEED);
        this.flightDir = look.clone();

        int smoothing = Math.max(2, Math.min(5, owner.getPing() / 50));
        ItemStack shown = stack.clone();
        shown.setAmount(1);
        this.display =
                world.spawn(
                        location(pos),
                        ItemDisplay.class,
                        d -> {
                            d.setItemStack(shown);
                            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                            d.setBillboard(Display.Billboard.FIXED);
                            d.setBrightness(new Display.Brightness(15, 15));
                            d.setTeleportDuration(smoothing);
                            d.setInterpolationDuration(1);
                            d.setPersistent(false);
                            d.setInvulnerable(true);
                        });
        updateDisplay();

        world.playSound(
                owner.getLocation(), Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.0f, 1.0f);
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::safeTick, 1L, 1L);
    }

    private void safeTick() {
        if (finished) return;
        try {
            tick();
        } catch (Throwable t) {
            // Whatever went wrong, the disc must not be lost.
            plugin.getLogger().log(Level.SEVERE, "Throwing disc tick failed", t);
            end();
        }
    }

    private void tick() {
        age++;
        spin = (spin + SPIN_RADIANS_PER_TICK) % (float) (Math.PI * 2.0);
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null
                || !owner.isOnline()
                || owner.isDead()
                || !owner.getWorld().equals(world)
                || returnTicks > MAX_LIFETIME_TICKS
                || !display.isValid()
                || !world.isChunkLoaded(pos.getBlockX() >> 4, pos.getBlockZ() >> 4)) {
            end();
            return;
        }
        if (returning) {
            returnTicks++;
            tickReturn(owner);
        } else {
            tickOutbound(owner);
        }
    }

    private void tickOutbound(Player owner) {
        double speed = velocity.length();
        if (speed < 1.0E-3) {
            beginReturn();
            return;
        }
        Vector from = pos.clone();
        Vector dir = velocity.clone().multiply(1.0 / speed);
        // Never fly further than the range that is left, so the throw is exactly BASE_RANGE blocks.
        double travel = Math.min(speed, rangeLeft);

        RayTraceResult hit =
                world.rayTraceBlocks(
                        location(from), dir, travel, FluidCollisionMode.NEVER, true);
        boolean blocked = hit != null && hit.getHitBlock() != null;
        Vector end = blocked ? hit.getHitPosition().clone() : from.clone().add(dir.multiply(travel));

        hitEntitiesAlong(owner, from, end);
        rangeLeft -= from.distance(end);
        moveTo(end);

        if (blocked) {
            onBlockHit(owner, hit);
        } else if (rangeLeft <= 1.0E-6 || age > MAX_OUTBOUND_TICKS) {
            beginReturn();
        }
    }

    private void onBlockHit(Player owner, RayTraceResult hit) {
        world.playSound(
                location(pos), Sound.ITEM_TRIDENT_HIT_GROUND, SoundCategory.PLAYERS, 1.0f, 1.0f);
        Material type = hit.getHitBlock().getType();
        BlockFace face = hit.getHitBlockFace();
        if (face == null
                || isNoRicochet(type)
                || ricochets >= MAX_RICOCHETS
                || age - lastBounceAge < BOUNCE_COOLDOWN_TICKS) {
            velocity = new Vector();
            beginReturn();
            return;
        }
        lastBounceAge = age;
        Vector normal = face.getDirection();
        Vector reflected =
                velocity.clone().subtract(normal.clone().multiply(2.0 * velocity.dot(normal)));
        reflected.multiply(BOUNCE_SPEED_RETAIN);
        if (reflected.length() < MIN_BOUNCE_SPEED) {
            reflected = reflected.normalize().multiply(MIN_BOUNCE_SPEED);
        }
        velocity = reflected;
        moveTo(pos.clone().add(normal.multiply(0.1)));
        ricochets++;
        rangeLeft += RICOCHET_RANGE_BONUS;

        world.spawnParticle(Particle.CRIT, location(pos), 4, 0.05, 0.05, 0.05, 0.15);
    }

    private static boolean isNoRicochet(Material type) {
        return NO_RICOCHET.contains(type)
                || Tag.WOOL.isTagged(type)
                || Tag.WOOL_CARPETS.isTagged(type)
                || Tag.BEDS.isTagged(type);
    }

    private void beginReturn() {
        returning = true;
        returnTicks = 0;
        heading = velocity.clone();
        stuckTicks = 0;
        path = List.of();
        pathIndex = 0;
        noProgressTicks = 0;
        bestRemaining = Double.MAX_VALUE;
        ticksSinceRepath = REPATH_INTERVAL;
        lastHitTick.clear();
        world.playSound(
                location(pos), Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 0.3f, 1.8f);
    }

    private void tickReturn(Player owner) {
        Vector from = pos.clone();
        if (DiscPathfinder.pointBlocked(world, from)) {
            // Ended up inside a block (ricochet at a corner, etc.): put the disc back in open space first.
            Vector snapped = DiscPathfinder.freeCentre(world, from);
            if (snapped != null) {
                pos = snapped;
                from = snapped.clone();
            }
        }
        Location ownerLocation = owner.getLocation();
        Vector target =
                new Vector(
                        ownerLocation.getX(),
                        ownerLocation.getY() + owner.getHeight() * 0.6,
                        ownerLocation.getZ());
        double distance = from.distance(target);
        if (distance <= CATCH_DISTANCE && DiscPathfinder.lineClear(world, from, target)) {
            world.playSound(
                    location(pos), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.5f);
            end();
            return;
        }

        Vector aim;
        boolean clearToOwner = DiscPathfinder.sweep(world, from, target) == null;
        if (clearToOwner) {
            aim = target;
            path = List.of();
            // The moment line of sight breaks, plan a route immediately.
            ticksSinceRepath = REPATH_INTERVAL;
        } else {
            ticksSinceRepath++;
            boolean due =
                    ticksSinceRepath >= REPATH_INTERVAL
                            || target.distanceSquared(pathGoal) > REPATH_GOAL_DRIFT_SQ
                            || (path.isEmpty() && ticksSinceRepath >= 3);
            if (due) repath(from, target);
            if (path.isEmpty()) {
                // No route right now: hold position, and give up (the item drops where the disc is) if it stays that way.
                velocity = new Vector();
                trackProgress(from.distance(target));
                return;
            }
            // Cut corners: skip ahead to the furthest upcoming waypoint the disc can fly to directly.
            int limit = Math.min(path.size() - 1, pathIndex + PATH_LOOKAHEAD);
            for (int i = limit; i > pathIndex; i--) {
                if (DiscPathfinder.sweep(world, from, path.get(i)) == null) {
                    pathIndex = i;
                    break;
                }
            }
            while (pathIndex < path.size() - 1
                    && from.distanceSquared(path.get(pathIndex)) < WAYPOINT_REACHED_SQ) {
                pathIndex++;
            }
            aim = path.get(pathIndex);
        }

        // Only give up when the disc is genuinely stuck: any real progress towards the owner resets the timer.
        if (trackProgress(aim == target ? distance : pathRemaining(from, target))) return;

        Vector toAim = aim.clone().subtract(from);
        double aimDistance = toAim.length();
        Vector desired =
                aimDistance < 1.0E-4 ? new Vector() : toAim.clone().multiply(RETURN_SPEED / aimDistance);
        Vector steer = heading.clone().add(desired.clone().subtract(heading).multiply(RETURN_TURN_RATE));
        Vector move = (aim == target && steer.length() >= aimDistance) ? toAim : steer;

        Vector end = from.clone().add(move);
        DiscPathfinder.Hit hit = DiscPathfinder.sweep(world, from, end);
        if (hit != null) {
            // Never pass through a block: stop at the surface, slide along it with whatever movement is
            // left, and re-plan next tick.
            Vector normal = hit.face().getDirection();
            double total = move.length();
            double used = from.distance(hit.position());
            double remain = total > 1.0E-6 ? Math.max(0.0, 1.0 - used / total) : 0.0;
            Vector slide = move.clone().subtract(normal.clone().multiply(move.dot(normal))).multiply(remain);
            Vector slideStart = hit.position().clone().add(normal.clone().multiply(0.01));
            Vector slideEnd = slideStart.clone().add(slide);
            DiscPathfinder.Hit slideHit = DiscPathfinder.sweep(world, slideStart, slideEnd);
            end = slideHit != null ? slideHit.position() : slideEnd;
            steer = desired;
            ticksSinceRepath = REPATH_INTERVAL;
        }

        if (end.clone().subtract(from).lengthSquared() < STUCK_EPSILON_SQ) {
            if (++stuckTicks >= STUCK_TICKS) {
                // Wedged against geometry: snap to the middle of a free cell and carry on from there.
                Vector snapped = DiscPathfinder.freeCentre(world, end);
                if (snapped != null) end = snapped;
                steer = desired;
                stuckTicks = 0;
                ticksSinceRepath = REPATH_INTERVAL;
            }
        } else {
            stuckTicks = 0;
        }

        heading = steer;
        // The velocity is the real (collision-clamped) movement, which is what hits and knockback use.
        velocity = end.clone().subtract(from);
        hitEntitiesAlong(owner, from, end);
        moveTo(end);
    }

    private void repath(Vector from, Vector target) {
        ticksSinceRepath = 0;
        pathGoal = target.clone();
        List<Vector> found = DiscPathfinder.findPath(world, from, target);
        if (found == null || found.isEmpty()) {
            path = List.of();
            pathIndex = 0;
            return;
        }
        path = found;
        pathIndex = 0;
    }

    /** Estimated distance still to fly: to the next waypoint, along the rest of the route, then to the owner. */
    private double pathRemaining(Vector from, Vector target) {
        Vector last = from;
        double total = 0.0;
        for (int i = pathIndex; i < path.size(); i++) {
            total += last.distance(path.get(i));
            last = path.get(i);
        }
        return total + last.distance(target);
    }

    /**
     * Records whether the disc got closer to the owner. Returns true (and drops the item where the disc
     * is) once it has gone {@link #NO_PROGRESS_DROP_TICKS} without improving on its best distance.
     */
    private boolean trackProgress(double remaining) {
        if (remaining < bestRemaining - PROGRESS_EPSILON) {
            bestRemaining = remaining;
            noProgressTicks = 0;
            return false;
        }
        if (++noProgressTicks > NO_PROGRESS_DROP_TICKS) {
            dropInPlace();
            return true;
        }
        return false;
    }

    private void hitEntitiesAlong(Player owner, Vector from, Vector to) {
        Vector segment = to.clone().subtract(from);
        double length = segment.length();
        BoundingBox sweep = BoundingBox.of(from, to).expand(HIT_MARGIN);

        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(sweep)) {
            if (entity instanceof LivingEntity living && Targets.isValidTarget(owner, living)) {
                candidates.add(living);
            }
        }
        candidates.sort(
                Comparator.comparingDouble(e -> e.getLocation().toVector().distanceSquared(from)));

        for (LivingEntity target : candidates) {
            BoundingBox box = target.getBoundingBox().expand(HIT_MARGIN);
            boolean inside = box.contains(from);
            if (!inside
                    && (length < 1.0E-6
                            || box.rayTrace(from, segment.clone().multiply(1.0 / length), length)
                                    == null)) {
                continue;
            }
            Integer last = lastHitTick.get(target.getUniqueId());
            if (last != null && age - last < HIT_COOLDOWN_TICKS) continue;
            damage(owner, target);
        }
    }

    private void damage(Player owner, LivingEntity target) {
        lastHitTick.put(target.getUniqueId(), age);
        double before = target.getHealth();
        target.damage(DISC_DAMAGE, owner);
        boolean landed = target.isDead() || target.getHealth() < before;
        if (landed) applyKnockback(target);
        world.playSound(
                target.getLocation(), Sound.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 1.0f, 1.0f);

        // The lightning does not depend on the disc's own damage getting through (armor, absorption hearts
        // and invincibility frames can swallow it): every time the disc hits something, it procs.
        // Passive: chain lightning to nearby players.
        lightning.chain(owner, target);
        // Primed throw: everything the disc hits is struck by lightning (once per throw).
        if (primed && struck.add(target.getUniqueId())) {
            lightning.strike(owner, target);
        }
    }

    /** Pushes the target along the disc's horizontal flight direction, like a vanilla knockback hit. */
    private void applyKnockback(LivingEntity target) {
        Vector horizontal = new Vector(velocity.getX(), 0.0, velocity.getZ());
        if (horizontal.lengthSquared() < 1.0E-6) return;
        AttributeInstance resistance = target.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        double strength = KNOCKBACK * (1.0 - (resistance == null ? 0.0 : resistance.getValue()));
        if (strength <= 0.0) return;
        Vector push = horizontal.normalize().multiply(strength);
        Vector current = target.getVelocity();
        double y = target.isOnGround() ? Math.min(0.4, current.getY() / 2.0 + strength) : current.getY();
        target.setVelocity(new Vector(current.getX() / 2.0 + push.getX(), y, current.getZ() / 2.0 + push.getZ()));
    }

    private void moveTo(Vector next) {
        pos = next;
        // Smooth the facing so a bounce doesn't snap the tilt.
        Vector target =
                velocity.lengthSquared() > 1.0E-6 ? velocity.clone().normalize() : flightDir;
        Vector blended =
                flightDir
                        .clone()
                        .add(target.clone().subtract(flightDir).multiply(FLIGHT_DIR_SMOOTHING));
        if (blended.lengthSquared() > 1.0E-6) flightDir = blended.normalize();
        updateDisplay();
    }

    private void updateDisplay() {
        Quaternionf left = new Quaternionf();
        double horizontal = Math.hypot(flightDir.getX(), flightDir.getZ());
        if (horizontal > 1.0E-3) {
            float hx = (float) (flightDir.getX() / horizontal);
            float hz = (float) (flightDir.getZ() / horizontal);
            double pitch = Math.toDegrees(Math.atan2(flightDir.getY(), horizontal));
            pitch = Math.max(-MAX_FLIGHT_PITCH, Math.min(MAX_FLIGHT_PITCH, pitch));
            left.rotationAxis((float) Math.toRadians(-pitch), hz, 0f, -hx);
        }
        left.mul(new Quaternionf().rotationY(spin));
        // Lies the sprite flat before it is spun and tilted.
        Quaternionf right = new Quaternionf().rotationX((float) Math.toRadians(90.0));

        display.setTransformation(
                new Transformation(new Vector3f(), left, new Vector3f(SCALE), right));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.teleport(location(pos));
    }

    private Location location(Vector at) {
        return new Location(world, at.getX(), at.getY(), at.getZ(), 0f, 0f);
    }

    /**
     * Ends the flight right now and gives the disc back: into the thrower's inventory (their old
     * slot if it is free), dropped at their feet if that is full, dropped where the disc is if the
     * thrower is dead or offline.
     */
    public void end() {
        finish(false);
    }

    /** Stuck for good (e.g. sealed off): the disc drops where it stopped instead of teleporting to the owner. */
    private void dropInPlace() {
        finish(true);
    }

    private void finish(boolean dropHere) {
        if (finished) return;
        finished = true;
        if (task != null) task.cancel();
        if (display != null && display.isValid()) display.remove();
        onEnd.accept(this);

        ItemStack disc = stack;
        stack = null;
        if (disc == null) return;

        Player owner = Bukkit.getPlayer(ownerId);
        if (dropHere) {
            drop(location(pos), disc);
        } else if (owner == null || !owner.isOnline() || owner.isDead()) {
            drop(location(pos), disc);
        } else {
            giveBack(owner, disc);
        }
    }

    private void giveBack(Player owner, ItemStack disc) {
        PlayerInventory inventory = owner.getInventory();
        ItemStack occupant = inventory.getItem(homeSlot);
        if (occupant == null || occupant.isEmpty()) {
            inventory.setItem(homeSlot, disc);
            return;
        }
        for (ItemStack leftover : inventory.addItem(disc).values()) {
            drop(owner.getLocation().add(0, 0.5, 0), leftover);
        }
    }

    private static void drop(Location at, ItemStack disc) {
        at.getWorld()
                .dropItem(
                        at,
                        disc,
                        (Item item) -> {
                            item.setVelocity(new Vector());
                            item.setInvulnerable(true);
                            item.setUnlimitedLifetime(true);
                        });
    }
}
