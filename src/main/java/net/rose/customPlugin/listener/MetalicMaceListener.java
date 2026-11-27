package net.rose.customPlugin.listener;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.effect.ChainEffects;
import net.rose.customPlugin.item.MetalicMace;
import net.rose.customPlugin.util.CooldownBars;
import net.rose.customPlugin.util.Targets;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Comparator;

public final class MetalicMaceListener implements Listener {

    private static final int PULL_RANGE = 20;
    private static final double AIM_RAY_SIZE = 1.5;         // fat ray: the crosshair only has to pass near the target
    private static final double AIM_CONE_DEGREES = 14.0;    // fallback: nearest target inside this cone around the crosshair
    private static final double PULL_SPEED = 1.0;           // blocks per tick
    private static final double PULL_LIFT = 0.2;            // keeps the target off the floor while dragged
    private static final double PULL_STOP_DISTANCE = 2.0;
    private static final int PULL_MAX_TICKS = 12;
    private static final long PULL_COOLDOWN_MS = 25_000;

    private static final String PULL_ID = "chain_pull";

    private final JavaPlugin plugin;
    private final MetalicMace mace;
    private final CooldownBars cooldowns;
    private final ChainEffects chains;

    public MetalicMaceListener(JavaPlugin plugin, MetalicMace mace, CooldownBars cooldowns, ChainEffects chains) {
        this.plugin = plugin;
        this.mace = mace;
        this.cooldowns = cooldowns;
        this.chains = chains;
    }

    /** Offhand key (F) while holding the Metalic Mace pulls the player you're looking at. The swap itself is always cancelled. */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!mace.isMetalicMace(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);
        pull(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getItem().getType() != Material.WIND_CHARGE) return;
        if (!event.getAction().isRightClick()) return;
        if (!chains.isChained(event.getPlayer())) return;

        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text("Chained - no wind charges", NamedTextColor.GRAY));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof WindCharge charge)) return;
        if (!(charge.getShooter() instanceof Player shooter)) return;
        if (chains.isChained(shooter)) event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cooldowns.resume(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        chains.clear(player);
        cooldowns.hideAll(player);
    }

    /**
     * Forgiving aim: first a wide ray along the crosshair (stopped by walls), then, if that finds nothing,
     * the target closest to the crosshair inside a cone (it must be in line of sight).
     */
    private LivingEntity findTarget(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();

        RayTraceResult blocks = player.getWorld().rayTraceBlocks(
                eye, direction, PULL_RANGE, FluidCollisionMode.NEVER, true);
        double reach = blocks == null ? PULL_RANGE : blocks.getHitPosition().distance(eye.toVector());

        RayTraceResult hit = player.getWorld().rayTraceEntities(eye, direction, reach, AIM_RAY_SIZE,
                e -> Targets.isValidTarget(player, e));
        if (hit != null && hit.getHitEntity() instanceof LivingEntity living) return living;

        double minDot = Math.cos(Math.toRadians(AIM_CONE_DEGREES));
        return player.getWorld().getNearbyLivingEntities(eye, PULL_RANGE).stream()
                .filter(e -> Targets.isValidTarget(player, e))
                .filter(e -> {
                    Vector toTarget = e.getLocation().add(0, e.getHeight() * 0.5, 0).toVector().subtract(eye.toVector());
                    double length = toTarget.length();
                    return length > 0.1 && length <= PULL_RANGE && toTarget.multiply(1.0 / length).dot(direction) >= minDot;
                })
                .filter(player::hasLineOfSight)
                .min(Comparator.comparingDouble(e -> -aimDot(eye, direction, e)))
                .orElse(null);
    }

    private static double aimDot(Location eye, Vector direction, LivingEntity entity) {
        Vector toTarget = entity.getLocation().add(0, entity.getHeight() * 0.5, 0).toVector().subtract(eye.toVector());
        return toTarget.normalize().dot(direction);
    }

    private void pull(Player player) {
        if (cooldowns.isOnCooldown(player, PULL_ID)) return; // boss bar already shows the time left

        LivingEntity target = findTarget(player);
        if (target == null) {
            player.sendActionBar(Component.text("Look at a target to use Chain Pull", NamedTextColor.RED));
            return;
        }

        cooldowns.start(player, PULL_ID, "Chain Pull", BossBar.Color.PURPLE, PULL_COOLDOWN_MS);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.8f);

        ChainEffects.Link link = chains.createLink();

        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || Targets.isGone(target)
                        || !player.getWorld().equals(target.getWorld())) {
                    link.remove();
                    cancel();
                    return;
                }

                boolean arrived = player.getLocation().distance(target.getLocation()) <= PULL_STOP_DISTANCE;
                if (arrived || ticks >= PULL_MAX_TICKS) {
                    link.remove();
                    chains.bind(player, target);
                    cancel();
                    return;
                }

                Location hand = player.getEyeLocation().subtract(0, 0.4, 0);
                Location chest = target.getLocation().add(0, 1.0, 0);
                link.update(hand, chest);

                Vector toward = player.getLocation().toVector().subtract(target.getLocation().toVector());
                Vector velocity = toward.normalize().multiply(PULL_SPEED);
                velocity.setY(Math.max(velocity.getY(), PULL_LIFT));
                target.setVelocity(velocity);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
