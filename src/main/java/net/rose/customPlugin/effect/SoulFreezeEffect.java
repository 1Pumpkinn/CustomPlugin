package net.rose.customPlugin.effect;

import net.rose.customPlugin.util.Targets;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Soul-sand themed visuals and sounds on whoever the Bloodthirst Scythe freezes. Purely cosmetic. */
public final class SoulFreezeEffect {

    private static final BlockData SOUL_SAND = Material.SOUL_SAND.createBlockData();

    private final JavaPlugin plugin;

    public SoulFreezeEffect(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Sound and a burst on the victim the moment they freeze, then souls rising off them until it wears off. */
    public void playFrozen(LivingEntity victim, int ticks) {
        Location at = victim.getLocation().add(0, 1.0, 0);
        victim.getWorld().playSound(at, Sound.PARTICLE_SOUL_ESCAPE, 1f, 1.0f);
        victim.getWorld().playSound(at, Sound.BLOCK_SOUL_SAND_BREAK, 1f, 0.8f);
        victim.getWorld().spawnParticle(Particle.SOUL, at, 20, 0.4, 0.6, 0.4, 0.05);

        new BukkitRunnable() {
            private int elapsed = 0;

            @Override
            public void run() {
                if (Targets.isGone(victim) || elapsed >= ticks) {
                    cancel();
                    return;
                }
                Location now = victim.getLocation();
                World world = victim.getWorld();
                world.spawnParticle(Particle.BLOCK, now.clone().add(0, 0.1, 0), 6, 0.4, 0.05, 0.4, 0.0, SOUL_SAND);
                if (elapsed % 2 == 0) {
                    world.spawnParticle(Particle.SOUL, now.clone().add(0, 0.5, 0), 2, 0.3, 0.4, 0.3, 0.03);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, now.clone().add(0, 1.0, 0), 1, 0.3, 0.5, 0.3, 0.0);
                }
                elapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
