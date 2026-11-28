package net.rose.customPlugin.util;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Steers arrows onto a target for a short while, so they still connect when the target moves. */
public final class ArrowHoming {

    private final JavaPlugin plugin;

    public ArrowHoming(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Height to aim at on an entity: about chest level (1.0 for a player). */
    public static double chest(LivingEntity entity) {
        return entity.getHeight() * 0.55;
    }

    /**
     * Steers the arrow onto the target for up to {@code maxTicks}. Gravity is switched off while it homes (the arrow
     * flies dead straight at the target) and back on afterwards.
     */
    public void home(Arrow arrow, LivingEntity target, double speed, int maxTicks) {
        arrow.setGravity(false);
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isInBlock() || arrow.isOnGround()
                        || Targets.isGone(target) || ticks++ >= maxTicks) {
                    if (arrow.isValid()) arrow.setGravity(true);
                    cancel();
                    return;
                }
                Vector toTarget = target.getLocation().add(0, chest(target), 0).toVector()
                        .subtract(arrow.getLocation().toVector());
                if (toTarget.lengthSquared() < 0.01) return;
                arrow.setVelocity(toTarget.normalize().multiply(speed));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Aim assist: whoever is within {@code raySize} of the line the arrow is flying along (and not behind a wall)
     * gets the arrow steered onto them, at the speed it was shot at.
     */
    public void assist(Player shooter, Arrow arrow, double range, double raySize, int ticks) {
        if (!arrow.isValid() || arrow.isInBlock()) return;

        Vector velocity = arrow.getVelocity();
        double speed = velocity.length();
        if (speed < 0.1) return;

        LivingEntity target = Aim.firstTarget(shooter, arrow.getLocation(), velocity.clone().normalize(), range, raySize);
        if (target != null) home(arrow, target, speed, ticks);
    }
}
