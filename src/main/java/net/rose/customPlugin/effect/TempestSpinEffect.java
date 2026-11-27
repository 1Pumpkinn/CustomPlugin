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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A trident that spins around the victim it hit like a helicopter blade, dealing a small amount of true
 * damage to them every tick. The orbit is done with the display's transformation (the client interpolates
 * it), the same way as {@link AxeSpinEffect}.
 */
public final class TempestSpinEffect {

    private static final int DURATION_TICKS = 60;                     // 3s
    private static final double TICK_DAMAGE = 0.2;                    // per tick: 0.1 heart, 6 hearts over the full spin
    private static final double ORBIT_RADIUS = 1.7;
    private static final float TRIDENT_SCALE = 2.0f;
    private static final double SPIN_PER_TICK = -Math.toRadians(36);  // 2 orbits per second

    /** Turns the trident about its own centre if its tip points the wrong way (0 = tip outward). */
    private static final double FACING_OFFSET_DEGREES = 0;

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
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(transform(0));
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
                double previous = angle;
                angle += SPIN_PER_TICK;

                display.teleport(centre);
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(2);
                display.setTransformation(transform(angle));

                // Damage every tick. Knockback is undone so the victim isn't launched 20 times a second.
                Vector velocity = victim.getVelocity();
                deaths.mark(victim, caster, DeathMessages.TEMPEST_TRIDENT);
                if (TrueDamage.of(TICK_DAMAGE).attacker(caster).type(DamageType.MAGIC).apply(victim)) {
                    victim.setVelocity(velocity);
                }

                World w = centre.getWorld();
                Location head = orbit(centre, angle, ORBIT_RADIUS + 0.8);
                w.spawnParticle(Particle.CLOUD, head, 2, 0.1, 0.1, 0.1, 0.01);
                w.spawnParticle(Particle.ELECTRIC_SPARK, head, 2, 0.15, 0.15, 0.15, 0.05);
                w.spawnParticle(Particle.CLOUD, orbit(centre, (previous + angle) / 2, ORBIT_RADIUS + 0.8),
                        1, 0.1, 0.1, 0.1, 0.01);
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

    /** Trident lying flat, centre {@link #ORBIT_RADIUS} from the victim at {@code angle}, tip pointing away from them. */
    private static Transformation transform(double angle) {
        Vector3f offset = new Vector3f(
                (float) (Math.cos(angle) * ORBIT_RADIUS), 0f, (float) (Math.sin(angle) * ORBIT_RADIUS));

        // Flat sprite: the tip diagonal points at heading -45 degrees; rotateY(phi) lowers a heading by phi.
        float yaw = (float) -(angle + Math.toRadians(45 + FACING_OFFSET_DEGREES));
        Quaternionf rotation = new Quaternionf()
                .rotateY(yaw)
                .rotateX((float) Math.toRadians(-90));

        return new Transformation(offset, rotation,
                new Vector3f(TRIDENT_SCALE, TRIDENT_SCALE, TRIDENT_SCALE), new Quaternionf());
    }
}
