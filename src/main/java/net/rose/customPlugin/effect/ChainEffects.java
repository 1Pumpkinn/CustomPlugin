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
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
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
import java.util.List;
import java.util.Map;
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

    private static final BlockData CHAIN = chainMaterial().createBlockData();

    private final JavaPlugin plugin;
    private final NamespacedKey jumpLockKey;
    private final Map<UUID, Bind> binds = new HashMap<>();

    public ChainEffects(JavaPlugin plugin) {
        this.plugin = plugin;
        this.jumpLockKey = new NamespacedKey(plugin, "chain_jump_lock");
    }

    /** Whether the player is currently bound by somebody's chain. */
    public boolean isChained(Player player) {
        return binds.containsKey(player.getUniqueId());
    }

    /** A chain stretched between two moving points (used for the pull). Call {@link Link#remove()} when done. */
    public Link createLink() {
        return new Link();
    }

    /** Slows the target for 5s and stops them jumping or using wind charges for a few seconds. */
    public void bind(Player caster, LivingEntity target) {
        if (!Targets.isValidTarget(caster, target)) return;
        release(target);

        Bind bind = new Bind(caster);
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

    private static BlockDisplay spawnSegment(Location at) {
        return at.getWorld().spawn(flat(at), BlockDisplay.class, display -> {
            display.setBlock(CHAIN);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setPersistent(false);
            display.setTeleportDuration(SMOOTHING_TICKS);
            display.setInterpolationDuration(SMOOTHING_TICKS);
        });
    }

    /** Points the chain's long axis along direction and stretches it to length, keeping the block centred on the entity. */
    private static void orient(BlockDisplay display, Vector direction, double length) {
        Quaternionf rotation = new Quaternionf().rotationTo(
                new Vector3f(0f, 1f, 0f),
                new Vector3f((float) direction.getX(), (float) direction.getY(), (float) direction.getZ()));
        Vector3f scale = new Vector3f(CHAIN_THICKNESS, (float) length, CHAIN_THICKNESS);
        Vector3f translation = new Vector3f(scale.x * 0.5f, scale.y * 0.5f, scale.z * 0.5f)
                .rotate(rotation)
                .negate();

        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(translation, rotation, scale, new Quaternionf()));
    }

    /** The chain block (renamed to iron chain in 1.21.9), falling back to iron bars. */
    private static Material chainMaterial() {
        for (String name : new String[] {"IRON_CHAIN", "CHAIN"}) {
            Material material = Material.matchMaterial(name);
            if (material != null) return material;
        }
        return Material.IRON_BARS;
    }

    /** Chain blocks laid end to end between two points, reused and restretched on every update. */
    public static final class Link {
        private final List<BlockDisplay> segments = new ArrayList<>();

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

            Vector direction = delta.multiply(1.0 / length);
            double segmentLength = length / count;
            for (int i = 0; i < count; i++) {
                Location middle = flat(from).add(direction.clone().multiply(segmentLength * (i + 0.5)));
                if (i >= segments.size()) segments.add(spawnSegment(middle));

                BlockDisplay display = segments.get(i);
                orient(display, direction, segmentLength);
                display.teleport(middle);
            }
        }

        public void remove() {
            for (BlockDisplay display : segments) {
                display.remove();
            }
            segments.clear();
        }
    }

    private static final class Bind {
        private final Player caster;
        private BukkitTask task;

        private Bind(Player caster) {
            this.caster = caster;
        }
    }
}
