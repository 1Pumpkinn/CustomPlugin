package net.rose.customPlugin.listener.bow;

import net.rose.customPlugin.effect.ArrowBounce;
import net.rose.customPlugin.util.ArrowHoming;
import net.rose.customPlugin.api.PrimedAbility;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.api.AbilityListener;
import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.item.bow.TricksterBow;
import net.rose.customPlugin.util.Targets;
import net.rose.customPlugin.util.TrueDamage;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class TricksterBowListener extends AbilityListener<TricksterBow> {

    private static final int EXTRA_ARROWS = 2;               // 3 arrows per shot with the real one
    private static final double SPREAD_DEGREES = 6.0;
    private static final double DAMAGE_MULTIPLIER = 1.5;

    private static final int MAX_BOUNCES = 3;                // per arrow

    // Aim assist for primed arrows: a target within ASSIST_RAY_SIZE of an arrow's line gets steered onto.
    private static final double ASSIST_RANGE = 30.0;
    private static final double ASSIST_RAY_SIZE = 1.2;
    private static final int ASSIST_TICKS = 40;

    private static final double SWAP_CHANCE = 0.10;

    private static final long SHOT_COOLDOWN_MS = 15_000;

    private final PrimedAbility shot;
    private final ArrowBounce arrows;
    private final ArrowHoming homing;

    public TricksterBowListener(WeaponContext context, TricksterBow bow) {
        super(context, bow);
        this.shot = primedAbility("trickster_shot", "Trickster Shot", BossBar.Color.PURPLE, SHOT_COOLDOWN_MS);
        this.homing = new ArrowHoming(plugin);
        this.arrows = new ArrowBounce(plugin, homing);
    }

    /** Offhand key (F) while holding the Trickster Bow primes the next shot. The swap itself is always cancelled. */
    @Override
    protected void onOffhand(Player player, boolean sneaking) {
        if (shot.prime(player)) player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.6f);
    }

    /**
     * Primed shot: the arrow becomes the middle of a 3 arrow fan, and every arrow is stronger and can bounce.
     * Unprimed shots are plain arrows (the passive still applies to them).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) return;
        if (!weapon.isWeapon(event.getBow())) return;
        if (!(event.getProjectile() instanceof Arrow main)) return;

        if (!shot.spend(shooter)) {
            arrows.tag(main, 0, null); // not primed: no fan, no bounce, but the hotbar passive still rolls
            return;
        }

        main.setDamage(main.getDamage() * DAMAGE_MULTIPLIER);
        arrows.tag(main, MAX_BOUNCES, null);

        Vector velocity = main.getVelocity();
        // The fan swings sideways in the shooter's view (around the axis pointing out of the top of their head),
        // not around the world's Y axis, or it would squash together when they look up or down.
        Vector forward = shooter.getEyeLocation().getDirection();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
        Vector up = right.lengthSquared() < 1.0E-6 ? new Vector(0, 0, 1) : right.crossProduct(forward).normalize();

        List<Arrow> fan = new ArrayList<>();
        fan.add(main);
        for (int i = 0; i < EXTRA_ARROWS; i++) {
            double side = i % 2 == 0 ? -1 : 1;
            double angle = Math.toRadians(side * SPREAD_DEGREES * (i / 2 + 1));
            Vector fanned = velocity.clone().rotateAroundAxis(up, angle);

            fan.add(shooter.getWorld().spawn(main.getLocation(), Arrow.class, extra -> {
                extra.setShooter(shooter);
                extra.setVelocity(fanned);
                extra.setCritical(main.isCritical());
                extra.setDamage(main.getDamage());
                extra.setFireTicks(main.getFireTicks());
                extra.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrows.tag(extra, MAX_BOUNCES, null);
            }));
        }

        // A tick later, once every arrow is in the world, each one is steered onto whoever it is flying at.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Arrow arrow : fan) homing.assist(shooter, arrow, ASSIST_RANGE, ASSIST_RAY_SIZE, ASSIST_TICKS);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;

        Integer bounces = arrows.bouncesLeft(arrow);
        if (bounces == null) return;

        if (!(event.getHitEntity() instanceof LivingEntity victim)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        // The shooter (and their pets) are never hit: a bounced arrow that crosses them just flies on.
        if (victim.equals(shooter) || (victim instanceof Tameable pet && shooter.getUniqueId().equals(pet.getOwnerUniqueId()))) {
            if (arrows.isPrimed(arrow)) event.setCancelled(true);
            return;
        }
        if (!Targets.isValidTarget(shooter, victim)) return;

        // Everyone this arrow's chain has already hit. A bounced arrow flies through all of them.
        Set<UUID> alreadyHit = arrows.hitBy(arrow);
        if (alreadyHit.contains(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        alreadyHit.add(victim.getUniqueId());

        // Three arrows (and their bounces) often land on the same target within a few ticks, and vanilla's hit
        // immunity throws away every one after the first. Primed arrows each get to hurt.
        if (arrows.isPrimed(arrow)) victim.setNoDamageTicks(0);

        // Only players have a hotbar to swap; mobs still get hit and the arrow still bounces on.
        if (victim instanceof Player target && ThreadLocalRandom.current().nextDouble() < SWAP_CHANCE) {
            swapHeldSlot(target);
        }

        if (bounces > 0) {
            double damage = arrow.getDamage();
            boolean critical = arrow.isCritical();
            arrows.bounceNextTick(shooter, victim, bounces - 1, damage, critical, alreadyHit);
        }
    }

    /** Primed shot arrows (the fan and every bounce) deal true damage: armor, enchants and resistance are ignored. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!arrows.isPrimed(arrow)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (event.getEntity().equals(arrow.getShooter())) { // never hurt the shooter
            event.setCancelled(true);
            return;
        }

        TrueDamage.makeTrue(event);
    }

    /** Passive: the slot the hit player is holding trades places with one other hotbar slot. Nothing else moves. */
    private void swapHeldSlot(Player victim) {
        PlayerInventory inventory = victim.getInventory();
        int held = inventory.getHeldItemSlot();

        // Pick one of the other eight slots.
        int other = ThreadLocalRandom.current().nextInt(8);
        if (other >= held) other++;

        ItemStack heldItem = inventory.getItem(held);
        inventory.setItem(held, inventory.getItem(other));
        inventory.setItem(other, heldItem);
        victim.updateInventory();

        victim.playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f);
    }
}
