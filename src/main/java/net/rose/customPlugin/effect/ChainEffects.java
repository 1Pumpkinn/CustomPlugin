package net.rose.customPlugin.effect;

import net.rose.customPlugin.util.Targets;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Metalic Mace's chain: a visual chain for the pull, and a bind on the pulled player that slows them,
 * stops them jumping (jump strength is zeroed) and stops them using wind charges (checked through
 * {@link #isChained(Player)}).
 */
public final class ChainEffects {

    private static final int BIND_TICKS = 160;              // 8s no jumping or wind charges
    private static final int SLOWNESS_TICKS = 160;
    private static final int SLOWNESS_AMPLIFIER = 1;        // Slowness II, same as the freeze

    private static final float CHAIN_THICKNESS = 1.5f;      // scales the chain's width so it reads from a distance
    private static final int SMOOTHING_TICKS = 2;
    private static final float SPIN_PER_TICK = 0.35f;      // radians the chain twists around its own length each tick (~1 turn a second)

    /** The long axis of customplugin:item/chain as modelled (it lies along X). Change this if the model is turned. */
    private static final Vector3f MODEL_AXIS = new Vector3f(1f, 0f, 0f);

    private final JavaPlugin plugin;
    private final NamespacedKey jumpLockKey;
    private final ItemStack chainItem;
    private final Map<UUID, Bind> binds = new HashMap<>();
    private final Set<Link> links = new HashSet<>();

    public ChainEffects(JavaPlugin plugin) {
        this.plugin = plugin;
        this.jumpLockKey = new NamespacedKey(plugin, "chain_jump_lock");

        // The chain model from the resource pack (customplugin:item/chain); the base item is never seen.
        ItemStack stack = new ItemStack(Material.STICK);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey(plugin, "chain"));
        stack.setItemMeta(meta);
        this.chainItem = stack;
    }

    /** Whether the player is currently bound by somebody's chain. */
    public boolean isChained(Player player) {
        return binds.containsKey(player.getUniqueId());
    }

    /** A chain stretched between two moving points (used for the pull). Call {@link Link#remove()} when done. */
    public Link createLink() {
        Link link = new Link();
        links.add(link);
        return link;
    }

    /** Slows the target for 5s and stops them jumping or using wind charges for a few seconds. */
    public void bind(Player caster, LivingEntity target) {
        if (!Targets.isValidTarget(caster, target)) return;
        release(target);

        Bind bind = new Bind();
        binds.put(target.getUniqueId(), bind);

        target.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, SLOWNESS_TICKS, SLOWNESS_AMPLIFIER, false, true, true));
        lockJump(target);

        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.7f);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.6f);

        bind.task = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (Targets.isGone(target) || ticks >= BIND_TICKS) {
                    release(target);
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Frees a player from any bind (e.g. on quit). */
    public void clear(Player player) {
        release(player);
    }

    /** Frees everyone; call from onDisable. */
    public void shutdown() {
        for (Link link : new ArrayList<>(links)) link.remove();
        for (UUID id : new ArrayList<>(binds.keySet())) {
            if (Bukkit.getEntity(id) instanceof LivingEntity living) release(living);
        }
        binds.clear();
    }

    private void release(LivingEntity target) {
        Bind bind = binds.remove(target.getUniqueId());
        if (bind == null) return;
        if (bind.task != null) bind.task.cancel();
        unlockJump(target);
    }

    private void lockJump(LivingEntity player) {
        AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump == null || jump.getModifier(jumpLockKey) != null) return;
        jump.addTransientModifier(new AttributeModifier(
                jumpLockKey, -1.0, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
    }

    private void unlockJump(LivingEntity player) {
        AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) jump.removeModifier(jumpLockKey);
    }

    /** A copy with no yaw or pitch: a display's own rotation turns the model, so it must not follow where a player looks. */
    private static Location flat(Location location) {
        Location copy = location.clone();
        copy.setYaw(0f);
        copy.setPitch(0f);
        return copy;
    }

    /**
     * Spawns a chain segment already pointing the right way. The transformation is set before the entity is
     * added and without an interpolation start, so it never shows (or swings in from) the default upright pose.
     */
    private ItemDisplay spawnSegment(Location at, Vector direction, double length, float roll) {
        ItemDisplay display = at.getWorld().spawn(flat(at), ItemDisplay.class, spawned -> {
            spawned.setItemStack(chainItem.clone());
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setPersistent(false);
            spawned.setTeleportDuration(0);
            spawned.setInterpolationDuration(0);
            spawned.setTransformation(transformation(direction, length, roll));
        });
        // Smoothing is turned on a tick later, once the client has the first pose.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!display.isValid()) return;
            display.setTeleportDuration(SMOOTHING_TICKS);
            display.setInterpolationDuration(SMOOTHING_TICKS);
        });
        return display;
    }

    /** Points the chain model's long axis along direction and stretches it to length. Item displays centre the model on the entity. */
    private static Transformation transformation(Vector direction, double length, float roll) {
        Quaternionf rotation = new Quaternionf().rotationTo(
                MODEL_AXIS,
                new Vector3f((float) direction.getX(), (float) direction.getY(), (float) direction.getZ()));
        // Twist around the chain's own length first, then point it along direction.
        rotation.rotateAxis(roll, MODEL_AXIS);
        Vector3f scale = new Vector3f((float) length, CHAIN_THICKNESS, CHAIN_THICKNESS);
        return new Transformation(new Vector3f(), rotation, scale, new Quaternionf());
    }

    private static void orient(ItemDisplay display, Vector direction, double length, float roll) {
        display.setInterpolationDelay(0);
        display.setTransformation(transformation(direction, length, roll));
    }

    /** Chain models laid end to end between two points, reused and restretched on every update. */
    public final class Link {
        private final List<ItemDisplay> segments = new ArrayList<>();
        private float roll = 0f;

        private Link() {
        }

        public void update(Location from, Location to) {
            Vector delta = to.toVector().subtract(from.toVector());
            double length = delta.length();
            if (length < 0.05) return;

            int count = Math.max(1, (int) Math.ceil(length));
            while (segments.size() > count) {
                segments.remove(segments.size() - 1).remove();
            }

            roll += SPIN_PER_TICK;
            Vector direction = delta.multiply(1.0 / length);
            double segmentLength = length / count;
            for (int i = 0; i < count; i++) {
                Location middle = flat(from).add(direction.clone().multiply(segmentLength * (i + 0.5)));
                if (i >= segments.size()) {
                    segments.add(spawnSegment(middle, direction, segmentLength, roll)); // born in the right pose
                    continue;
                }

                ItemDisplay display = segments.get(i);
                orient(display, direction, segmentLength, roll);
                display.teleport(middle);
            }
        }

        public void remove() {
            for (ItemDisplay display : segments) {
                display.remove();
            }
            segments.clear();
            links.remove(this);
        }
    }

    private static final class Bind {
        private BukkitTask task;
    }
}
