package net.rose.customPlugin.effect;

import net.rose.customPlugin.util.DeathMessages;
import net.rose.customPlugin.util.Targets;
import net.rose.customPlugin.util.TrueDamage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A trident that circles the victim it hit with its prongs pointing at them, dealing a small amount of true
 * damage to them every tick. It is drawn with the real 3D trident model (see {@link #displayItem()}) and hangs
 * out beyond the victim's hitbox, so it never passes through them. The orbit is done with the display's transformation (the
 * client interpolates it), the same way as {@link AxeSpinEffect}.
 */
public final class TempestSpinEffect {

    private static final int DURATION_TICKS = 60;                     // 3s
    /** True damage per tick: 4 hearts (8 health) spread over the whole spin. */
    private static final double TICK_DAMAGE = 8.0 / DURATION_TICKS;
    /** Gap between the victim's hitbox and the prong tips. */
    private static final double TIP_GAP = 0.6;
    private static final float TRIDENT_SCALE = 1.0f;
    /** Half the length of the 3D trident at scale 1 (about two blocks long). */
    private static final double HALF_LENGTH = 0.97 * TRIDENT_SCALE;
    private static final double SPIN_PER_TICK = -Math.toRadians(36);  // 2 orbits per second

    /**
     * How the 3D trident model is laid out in its own space when shown with the NONE transform: the direction
     * the prongs point, and the direction the prongs spread apart. If it flies sideways or handle-first,
     * these two are the only things to change.
     */
    private static final Vector3f MODEL_TIP_AXIS = new Vector3f(0f, 0f, 1f);
    private static final Vector3f MODEL_SPREAD_AXIS = new Vector3f(0f, 1f, 0f);

    /** Where the middle of the model sits in its own space (blocks at scale 1); the shaft runs away from the tip. */
    private static final Vector3f MODEL_CENTER = new Vector3f(0f, 0f, -0.72f);

    private record Active(BukkitTask task, ItemDisplay display) {
    }

    private final JavaPlugin plugin;
    private final DeathMessages deaths;
    private final Map<UUID, Active> active = new HashMap<>(); // keyed by victim

    public TempestSpinEffect(JavaPlugin plugin, DeathMessages deaths) {
        this.plugin = plugin;
        this.deaths = deaths;
    }

    public void start(Player caster, LivingEntity victim) {
        stop(victim);

        World world = victim.getWorld();
        ItemDisplay display = world.spawn(anchor(victim), ItemDisplay.class, d -> {
            d.setItemStack(displayItem());
            // NONE is not one of the 2D sprite contexts in items/tempest_trident.json, so it falls through
            // to the real 3D trident model (FIXED would give the flat sprite).
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(transform(0, orbitRadius(victim)));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setInterpolationDuration(2);
            d.setShadowRadius(0f);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });

        world.playSound(victim.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1.1f);
        world.playSound(victim.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.4f);

        BukkitTask task = new BukkitRunnable() {
            private int ticks = 0;
            private double angle = 0;

            @Override
            public void run() {
                if (!caster.isOnline() || Targets.isGone(victim) || ticks >= DURATION_TICKS
                        || !display.isValid() || !display.getWorld().equals(victim.getWorld())) {
                    stop(victim);
                    return;
                }

                Location centre = anchor(victim);
                double radius = orbitRadius(victim);
                angle += SPIN_PER_TICK;

                display.teleport(centre);
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(2);
                display.setTransformation(transform(angle, radius));

                // Damage every tick. Knockback is undone so the victim isn't launched 20 times a second.
                Vector velocity = victim.getVelocity();
                deaths.mark(victim, caster, DeathMessages.TEMPEST_TRIDENT);
                if (TrueDamage.of(TICK_DAMAGE).attacker(caster).type(DamageType.MAGIC).apply(victim)) {
                    victim.setVelocity(velocity);
                }

                World w = centre.getWorld();
                // Sparks at the prongs, a cloud at the handle.
                Location tip = orbit(centre, angle, radius - HALF_LENGTH);
                Location tail = orbit(centre, angle, radius + HALF_LENGTH);
                w.spawnParticle(Particle.ELECTRIC_SPARK, tip, 2, 0.15, 0.15, 0.15, 0.05);
                w.spawnParticle(Particle.CLOUD, tail, 2, 0.1, 0.1, 0.1, 0.01);
                if (ticks % 4 == 0) {
                    w.spawnParticle(Particle.SWEEP_ATTACK, centre, 1, 0.6, 0.4, 0.6, 0.0);
                }
                if (ticks % 5 == 0) {
                    w.playSound(centre, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 0.8f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        active.put(victim.getUniqueId(), new Active(task, display));
    }

    public void stop(LivingEntity victim) {
        Active a = active.remove(victim.getUniqueId());
        if (a == null) return;
        a.task().cancel();
        if (a.display().isValid()) a.display().remove();
    }

    /** Removes every spinning trident; call from onDisable. */
    public void removeAll() {
        for (Active a : active.values()) {
            a.task().cancel();
            if (a.display().isValid()) a.display().remove();
        }
        active.clear();
    }

    /** Needs the item model of the real Tempest Trident (customplugin:tempest_trident) or it shows as a vanilla trident. */
    private ItemStack displayItem() {
        ItemStack item = new ItemStack(Material.TRIDENT);
        item.editMeta(meta -> meta.setItemModel(new NamespacedKey(plugin, "tempest_trident")));
        return item;
    }

    /** Chest height on the victim, with yaw/pitch zeroed so the display never tilts with their camera. */
    private static Location anchor(LivingEntity victim) {
        Location loc = victim.getLocation();
        loc.setYaw(0f);
        loc.setPitch(0f);
        return loc.add(0, victim.getHeight() * 0.55, 0);
    }

    private static Location orbit(Location centre, double angle, double radius) {
        return centre.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }

    /** Distance from the victim's centre to the middle of the trident: hitbox edge, gap, then half its length. */
    private static double orbitRadius(LivingEntity victim) {
        return victim.getWidth() / 2.0 + TIP_GAP + HALF_LENGTH;
    }

    /**
     * The trident at {@code angle} on the orbit, lying along the radius with its prongs pointing at the victim
     * and its handle pointing away, prongs spread sideways.
     */
    private static Transformation transform(double angle, double radius) {
        Vector3f outward = new Vector3f((float) Math.cos(angle), 0f, (float) Math.sin(angle));
        Vector3f inward = new Vector3f(outward).negate();
        Vector3f sideways = new Vector3f(-outward.z, 0f, outward.x);

        // Rotation that takes the model's (spread, tip) axes onto the world's (sideways, inward) axes.
        Matrix3f model = frame(MODEL_SPREAD_AXIS, MODEL_TIP_AXIS);
        Matrix3f world = frame(sideways, inward);
        Quaternionf rotation = new Quaternionf().setFromNormalized(world.mul(model.transpose()));

        // Put the middle of the model (not its origin) on the orbit.
        Vector3f offset = new Vector3f(outward).mul((float) radius);
        Vector3f centreShift = new Vector3f(MODEL_CENTER).mul(TRIDENT_SCALE).rotate(rotation);
        offset.sub(centreShift);

        return new Transformation(offset, rotation,
                new Vector3f(TRIDENT_SCALE, TRIDENT_SCALE, TRIDENT_SCALE), new Quaternionf());
    }

    /** Right-handed orthonormal frame with columns (spread, tip, spread x tip). */
    private static Matrix3f frame(Vector3f spread, Vector3f tip) {
        Vector3f third = new Vector3f(spread).cross(tip);
        return new Matrix3f(spread.x, spread.y, spread.z, tip.x, tip.y, tip.z, third.x, third.y, third.z);
    }
}
