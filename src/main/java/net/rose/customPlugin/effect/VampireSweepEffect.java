package net.rose.customPlugin.effect;

import net.rose.customPlugin.util.DeathMessages;
import net.rose.customPlugin.util.Targets;
import net.rose.customPlugin.util.TrueDamage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The Bloodthirst Scythe's offhand: a wide sweep in front of the player that deals true damage to everything in
 * the arc, and heals the player for what it took (2.5 hearts per sweep, however many targets it hits).
 */
public final class VampireSweepEffect {

    private static final double RANGE = 3.5;
    /** Half the width of the arc, as the cosine of the angle off the facing direction (0.5 = 60 degrees each side). */
    private static final double MIN_COS = 0.5;
    private static final double MAX_VERTICAL = 2.0;
    /** True damage to each target: 2.5 hearts. */
    private static final double DAMAGE = 5.0;
    /** Health the player gets back: 2.5 hearts, once per sweep. */
    private static final double LIFESTEAL = 5.0;

    private static final Particle.DustOptions BLOOD_DUST = new Particle.DustOptions(Color.fromRGB(0xC4, 0x12, 0x1F), 1.3f);

    private final DeathMessages deaths;

    public VampireSweepEffect(DeathMessages deaths) {
        this.deaths = deaths;
    }

    /** Sweeps in front of the player. Returns false (and does nothing) if there was nobody to hit. */
    public boolean cast(Player player) {
        Vector facing = player.getEyeLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 1.0E-6) facing = player.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 1.0E-6) return false;
        facing.normalize();

        Location origin = player.getLocation();
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(RANGE + 1, MAX_VERTICAL + 1, RANGE + 1)) {
            if (!Targets.isValidTarget(player, entity)) continue;
            LivingEntity living = (LivingEntity) entity;

            Vector to = living.getLocation().toVector().subtract(origin.toVector());
            if (Math.abs(to.getY()) > MAX_VERTICAL + living.getHeight()) continue;
            to.setY(0);
            double distance = to.length() - living.getWidth() / 2.0;
            if (distance > RANGE) continue;
            if (to.lengthSquared() > 1.0E-6 && to.normalize().dot(facing) < MIN_COS) continue;
            targets.add(living);
        }
        if (targets.isEmpty()) return false;

        World world = player.getWorld();
        drawSweep(player, facing);

        boolean landedAny = false;
        for (LivingEntity victim : targets) {
            deaths.mark(victim, player, DeathMessages.BLOODTHIRST_SCYTHE);
            if (TrueDamage.of(DAMAGE).attacker(player).type(DamageType.MAGIC).apply(victim)) {
                landedAny = true;
                Location chest = victim.getLocation().add(0, victim.getHeight() * 0.5, 0);
                world.spawnParticle(Particle.DUST, chest, 14, 0.3, 0.4, 0.3, 0.0, BLOOD_DUST);
            }
        }

        if (landedAny) {
            player.heal(LIFESTEAL, EntityRegainHealthEvent.RegainReason.CUSTOM);
            Location at = player.getLocation().add(0, 1.0, 0);
            world.playSound(at, Sound.ENTITY_GENERIC_DRINK, 0.8f, 0.7f);
        }
        return true;
    }

    /** A crescent of blood across the arc, with the vanilla sweep slash in the middle. */
    private void drawSweep(Player player, Vector facing) {
        World world = player.getWorld();
        Location base = player.getLocation().add(0, 1.0, 0);
        double halfAngle = Math.acos(MIN_COS);
        for (double a = -halfAngle; a <= halfAngle + 1.0E-6; a += Math.toRadians(10)) {
            Vector dir = facing.clone().rotateAroundY(a);
            for (double r = 1.5; r <= RANGE; r += 1.0) {
                Location point = base.clone().add(dir.clone().multiply(r));
                world.spawnParticle(Particle.DUST, point, 2, 0.08, 0.08, 0.08, 0.0, BLOOD_DUST);
            }
        }
        world.spawnParticle(Particle.SWEEP_ATTACK, base.clone().add(facing.clone().multiply(2.0)), 1, 0, 0, 0, 0);
        world.playSound(base, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        world.playSound(base, Sound.BLOCK_SOUL_SAND_BREAK, 0.8f, 1.4f);
    }
}
