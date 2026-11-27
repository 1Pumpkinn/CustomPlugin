package net.rose.customPlugin.listener.bow;

import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.item.bow.TricksterBow;
import net.rose.customPlugin.util.CooldownBars;
import net.rose.customPlugin.util.Targets;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;

public final class TricksterBowListener implements Listener {

    private static final int EXTRA_ARROWS = 2;               // 3 arrows per shot with the real one
    private static final double SPREAD_DEGREES = 6.0;
    private static final double DAMAGE_MULTIPLIER = 1.5;

    private static final int MAX_BOUNCES = 3;                // per arrow
    private static final double BOUNCE_RANGE = 4.0;               // next target must be within 4 blocks of the one just hit
    private static final double BOUNCE_SPEED = 2.5;
    private static final double BOUNCE_START_OFFSET = 0.9;   // spawn clear of the player that was just hit
    private static final double ARROW_DROP_COMPENSATION = 0.012;

    private static final int HOMING_TICKS = 30;             // bounced arrows steer onto their target so moving players still get hit

    private static final double SWAP_CHANCE = 0.10;

    private static final long SHOT_COOLDOWN_MS = 15_000;
    private static final String SHOT_ID = "trickster_shot";

    private final JavaPlugin plugin;
    private final TricksterBow bow;
    private final CooldownBars cooldowns;
    private final Set<UUID> armed = new HashSet<>();
    private final NamespacedKey bouncesKey;
    private final NamespacedKey hitKey;
    private final NamespacedKey primedKey;

    public TricksterBowListener(JavaPlugin plugin, TricksterBow bow, CooldownBars cooldowns) {
        this.plugin = plugin;
        this.bow = bow;
        this.cooldowns = cooldowns;
        this.bouncesKey = new NamespacedKey(plugin, "trickster_bounces");
        this.hitKey = new NamespacedKey(plugin, "trickster_hit");
        this.primedKey = new NamespacedKey(plugin, "trickster_primed");
    }

    /** Offhand key (F) while holding the Trickster Bow primes the next shot. The swap itself is always cancelled. */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!bow.isTricksterBow(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);
        prime(player);
    }

    /**
     * Primed shot: the arrow becomes the middle of a 3 arrow fan, and every arrow is stronger and can bounce.
     * Unprimed shots are plain arrows (the passive still applies to them).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) return;
        if (!bow.isTricksterBow(event.getBow())) return;
        if (!(event.getProjectile() instanceof Arrow main)) return;

        if (!armed.remove(shooter.getUniqueId())) {
            tag(main, 0, null); // not primed: no fan, no bounce, but the hotbar passive still rolls
            return;
        }
        cooldowns.unprime(shooter, SHOT_ID);
        cooldowns.start(shooter, SHOT_ID, "Trickster Shot", BossBar.Color.PURPLE, SHOT_COOLDOWN_MS);

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

        if (!(event.getHitEntity() instanceof LivingEntity victim)) return;
        if (!(arrow.getShooter() instanceof Player shooter) || !Targets.isValidTarget(shooter, victim)) return;

        // Everyone this arrow's chain has already hit. A bounced arrow flies through all of them.
        Set<UUID> alreadyHit = hitBy(arrow);
        if (alreadyHit.contains(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        alreadyHit.add(victim.getUniqueId());

        // Only players have a hotbar to swap; mobs still get hit and the arrow still bounces on.
        if (victim instanceof Player target && ThreadLocalRandom.current().nextDouble() < SWAP_CHANCE) {
            swapHeldSlot(target);
        }

        if (bounces > 0) {
            double damage = arrow.getDamage();
            boolean critical = arrow.isCritical();
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> bounce(shooter, victim, bounces - 1, damage, critical, alreadyHit));
        }
    }

    /** Primed shot arrows (the fan and every bounce) deal true damage: armor, enchants and resistance are ignored. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!arrow.getPersistentDataContainer().has(primedKey, PersistentDataType.BYTE)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        for (DamageModifier modifier : DamageModifier.values()) {
            if (modifier == DamageModifier.BASE || modifier == DamageModifier.ABSORPTION) continue;
            if (!event.isApplicable(modifier)) continue;
            event.setDamage(modifier, 0);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        armed.remove(event.getEntity().getUniqueId());
        cooldowns.unprime(event.getEntity(), SHOT_ID);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cooldowns.resume(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        armed.remove(player.getUniqueId());
        cooldowns.hideAll(player);
    }

    private void prime(Player player) {
        if (cooldowns.isOnCooldown(player, SHOT_ID)) return; // boss bar already shows the time left
        if (!armed.add(player.getUniqueId())) return;
        cooldowns.prime(player, SHOT_ID, "Trickster Shot", BossBar.Color.PURPLE);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.6f);
    }

    private void bounce(Player shooter, LivingEntity from, int bouncesLeft, double damage, boolean critical,
                        Set<UUID> alreadyHit) {
        if (!shooter.isOnline() || !from.isValid()) return;

        LivingEntity next = nearestTarget(shooter, from, alreadyHit);
        if (next == null) return;

        Location start = from.getLocation().add(0, chest(from), 0);
        Vector direction = next.getLocation().add(0, chest(next), 0).toVector().subtract(start.toVector());
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
            tag(arrow, bouncesLeft, alreadyHit);
            home(arrow, next);
        });

        world.playSound(start, Sound.ENTITY_ARROW_SHOOT, 1f, 1.5f);
        world.spawnParticle(Particle.CRIT, start, 12, 0.3, 0.3, 0.3, 0.2);
    }

    /** Steers a bounced arrow onto its target for a short while so it still connects if the target moves. */
    private void home(Arrow arrow, LivingEntity target) {
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isInBlock() || arrow.isOnGround()
                        || Targets.isGone(target) || ticks++ >= HOMING_TICKS) {
                    cancel();
                    return;
                }
                Vector toTarget = target.getLocation().add(0, chest(target), 0).toVector()
                        .subtract(arrow.getLocation().toVector());
                if (toTarget.lengthSquared() < 0.01) return;
                toTarget.normalize();
                toTarget.setY(toTarget.getY() + 0.02); // tiny lift to counter drop
                arrow.setVelocity(toTarget.normalize().multiply(BOUNCE_SPEED));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Height to aim at on an entity: about chest level (1.0 for a player). */
    private static double chest(LivingEntity entity) {
        return entity.getHeight() * 0.55;
    }

    /** Closest target, player or mob (not the shooter, not anyone this arrow already hit), within range and in line of sight. */
    private LivingEntity nearestTarget(Player shooter, LivingEntity from, Set<UUID> alreadyHit) {
        LivingEntity best = null;
        double bestDistance = BOUNCE_RANGE * BOUNCE_RANGE;
        for (LivingEntity candidate : from.getWorld().getNearbyLivingEntities(from.getLocation(), BOUNCE_RANGE)) {
            if (alreadyHit.contains(candidate.getUniqueId()) || !Targets.isValidTarget(shooter, candidate)) continue;

            double distance = from.getLocation().distanceSquared(candidate.getLocation());
            if (distance > bestDistance || !from.hasLineOfSight(candidate)) continue;

            best = candidate;
            bestDistance = distance;
        }
        return best;
    }

    private void tag(Arrow arrow, int bounces, Set<UUID> alreadyHit) {
        PersistentDataContainer data = arrow.getPersistentDataContainer();
        data.set(bouncesKey, PersistentDataType.INTEGER, bounces);
        if (bounces > 0 || (alreadyHit != null && !alreadyHit.isEmpty())) {
            data.set(primedKey, PersistentDataType.BYTE, (byte) 1); // part of the primed ability (fan or bounce)
        }
        if (alreadyHit != null && !alreadyHit.isEmpty()) {
            data.set(hitKey, PersistentDataType.STRING,
                    alreadyHit.stream().map(UUID::toString).collect(Collectors.joining(",")));
        }
    }

    /** The targets this arrow's bounce chain has already hit (a fresh, mutable set). */
    private Set<UUID> hitBy(Arrow arrow) {
        Set<UUID> hit = new HashSet<>();
        String stored = arrow.getPersistentDataContainer().get(hitKey, PersistentDataType.STRING);
        if (stored != null && !stored.isEmpty()) {
            Arrays.stream(stored.split(",")).map(UUID::fromString).forEach(hit::add);
        }
        return hit;
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
