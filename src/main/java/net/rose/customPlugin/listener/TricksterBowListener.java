package net.rose.customPlugin.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.item.TricksterBow;
import net.rose.customPlugin.util.Targets;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class TricksterBowListener implements Listener {

    private static final int EXTRA_ARROWS = 2;               // 3 arrows per shot with the real one
    private static final double SPREAD_DEGREES = 6.0;
    private static final double DAMAGE_MULTIPLIER = 1.5;

    private static final int MAX_BOUNCES = 3;                // per arrow
    private static final double BOUNCE_RANGE = 12.0;
    private static final double BOUNCE_SPEED = 2.5;
    private static final double BOUNCE_START_OFFSET = 0.9;   // spawn clear of the player that was just hit
    private static final double ARROW_DROP_COMPENSATION = 0.012;

    private static final double SWAP_CHANCE = 0.10;

    private final JavaPlugin plugin;
    private final TricksterBow bow;
    private final NamespacedKey bouncesKey;
    private final NamespacedKey lastVictimKey;

    public TricksterBowListener(JavaPlugin plugin, TricksterBow bow) {
        this.plugin = plugin;
        this.bow = bow;
        this.bouncesKey = new NamespacedKey(plugin, "trickster_bounces");
        this.lastVictimKey = new NamespacedKey(plugin, "trickster_last_victim");
    }

    /** The shot arrow becomes the middle of a 3 arrow fan, and every arrow is stronger and can bounce. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) return;
        if (!bow.isTricksterBow(event.getBow())) return;
        if (!(event.getProjectile() instanceof Arrow main)) return;

        main.setDamage(main.getDamage() * DAMAGE_MULTIPLIER);
        tag(main, MAX_BOUNCES, null);

        Vector velocity = main.getVelocity();
        for (int i = 0; i < EXTRA_ARROWS; i++) {
            double side = i % 2 == 0 ? -1 : 1;
            double angle = Math.toRadians(side * SPREAD_DEGREES * (i / 2 + 1));
            Vector fanned = velocity.clone().rotateAroundY(angle);

            shooter.getWorld().spawn(main.getLocation(), Arrow.class, extra -> {
                extra.setShooter(shooter);
                extra.setVelocity(fanned);
                extra.setCritical(main.isCritical());
                extra.setDamage(main.getDamage());
                extra.setFireTicks(main.getFireTicks());
                extra.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                tag(extra, MAX_BOUNCES, null);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;

        PersistentDataContainer data = arrow.getPersistentDataContainer();
        Integer bounces = data.get(bouncesKey, PersistentDataType.INTEGER);
        if (bounces == null) return;

        if (!(event.getHitEntity() instanceof Player victim)) return;
        if (!(arrow.getShooter() instanceof Player shooter) || !Targets.isValidPlayer(shooter, victim)) return;

        String lastVictim = data.get(lastVictimKey, PersistentDataType.STRING);
        if (victim.getUniqueId().toString().equals(lastVictim)) {
            event.setCancelled(true); // a bounced arrow flies through the player it just left
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() < SWAP_CHANCE) shuffleHotbar(victim);

        if (bounces > 0) {
            double damage = arrow.getDamage();
            boolean critical = arrow.isCritical();
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> bounce(shooter, victim, bounces - 1, damage, critical));
        }
    }

    private void bounce(Player shooter, Player from, int bouncesLeft, double damage, boolean critical) {
        if (!shooter.isOnline() || !from.isOnline()) return;

        Player next = nearestTarget(shooter, from);
        if (next == null) return;

        Location start = from.getLocation().add(0, 1.0, 0);
        Vector direction = next.getLocation().add(0, 1.0, 0).toVector().subtract(start.toVector());
        double distance = direction.length();
        if (distance < 0.1) return;

        direction.multiply(1.0 / distance);
        direction.setY(direction.getY() + distance * ARROW_DROP_COMPENSATION);
        direction.normalize();

        World world = from.getWorld();
        Location spawn = start.clone().add(direction.clone().multiply(BOUNCE_START_OFFSET));
        Vector velocity = direction.multiply(BOUNCE_SPEED);

        world.spawn(spawn, Arrow.class, arrow -> {
            arrow.setShooter(shooter);
            arrow.setVelocity(velocity);
            arrow.setDamage(damage);
            arrow.setCritical(critical);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            tag(arrow, bouncesLeft, from);
        });

        world.playSound(start, Sound.ENTITY_ARROW_SHOOT, 1f, 1.5f);
        world.spawnParticle(Particle.CRIT, start, 12, 0.3, 0.3, 0.3, 0.2);
    }

    /** Closest other player (not the shooter, not the one just hit) within range and in line of sight. */
    private Player nearestTarget(Player shooter, Player from) {
        Player best = null;
        double bestDistance = BOUNCE_RANGE * BOUNCE_RANGE;
        for (Player candidate : from.getWorld().getPlayers()) {
            if (candidate.equals(from) || !Targets.isValidPlayer(shooter, candidate)) continue;

            double distance = from.getLocation().distanceSquared(candidate.getLocation());
            if (distance > bestDistance || !from.hasLineOfSight(candidate)) continue;

            best = candidate;
            bestDistance = distance;
        }
        return best;
    }

    private void tag(Arrow arrow, int bounces, Player lastVictim) {
        PersistentDataContainer data = arrow.getPersistentDataContainer();
        data.set(bouncesKey, PersistentDataType.INTEGER, bounces);
        if (lastVictim != null) {
            data.set(lastVictimKey, PersistentDataType.STRING, lastVictim.getUniqueId().toString());
        }
    }

    /** Passive: the hit player's nine hotbar slots get shuffled around. */
    private void shuffleHotbar(Player victim) {
        PlayerInventory inventory = victim.getInventory();
        List<ItemStack> hotbar = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            hotbar.add(inventory.getItem(slot));
        }
        Collections.shuffle(hotbar);
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, hotbar.get(slot));
        }
        victim.updateInventory();

        victim.playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f);
    }
}
