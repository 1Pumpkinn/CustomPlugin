package net.rose.customPlugin.listener;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.effect.AxeSpinEffect;
import net.rose.customPlugin.effect.EruptionEffect;
import net.rose.customPlugin.item.VolcanicAxe;
import net.rose.customPlugin.util.CooldownBars;
import net.rose.customPlugin.util.Targets;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class VolcanicAxeListener implements Listener {

    private static final double ERUPTION_RANGE = 30.0;
    private static final double RAY_SIZE = 0.8;
    private static final long ERUPTION_COOLDOWN_MS = 30_000;
    private static final long SPIN_COOLDOWN_MS = 45_000;

    private static final String ERUPTION_ID = "volcanic_eruption";
    private static final String SPIN_ID = "volcanic_spin";

    private final VolcanicAxe axe;
    private final CooldownBars cooldowns;
    private final EruptionEffect eruption;
    private final AxeSpinEffect spin;

    public VolcanicAxeListener(VolcanicAxe axe, CooldownBars cooldowns,
                               EruptionEffect eruption, AxeSpinEffect spin) {
        this.axe = axe;
        this.cooldowns = cooldowns;
        this.eruption = eruption;
        this.spin = spin;
    }

    /**
     * Offhand key (F) while holding the Volcanic Axe:
     * normal = Eruption under the target you look at, crouching = spinning axe.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!axe.isVolcanicAxe(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);

        if (player.isSneaking()) {
            useSpin(player);
        } else {
            useEruption(player);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        spin.stop(event.getEntity());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cooldowns.resume(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        spin.stop(player);
        cooldowns.hideAll(player);
    }

    // ---------------------------------------------------------------- Abilities

    private void useEruption(Player player) {
        if (cooldowns.isOnCooldown(player, ERUPTION_ID)) return; // boss bar already shows the time left

        LivingEntity target = findTarget(player);
        if (target == null) {
            player.sendActionBar(Component.text("Look at a target to use Eruption", NamedTextColor.RED));
            return; // failed cast stays free (no cooldown)
        }

        cooldowns.start(player, ERUPTION_ID, "Eruption", BossBar.Color.RED, ERUPTION_COOLDOWN_MS);
        eruption.cast(player, EruptionEffect.groundBelow(target.getLocation()));
    }

    private void useSpin(Player player) {
        if (cooldowns.isOnCooldown(player, SPIN_ID)) return;

        cooldowns.start(player, SPIN_ID, "Spinning Axe", BossBar.Color.YELLOW, SPIN_COOLDOWN_MS);
        spin.start(player);
    }

    /** First valid target (player or mob) in the line of sight, stopped by walls. */
    private LivingEntity findTarget(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();

        RayTraceResult blocks = world.rayTraceBlocks(eye, dir, ERUPTION_RANGE, FluidCollisionMode.NEVER, true);
        double maxDistance = blocks == null ? ERUPTION_RANGE : blocks.getHitPosition().distance(eye.toVector());

        RayTraceResult hit = world.rayTraceEntities(eye, dir, maxDistance, RAY_SIZE,
                e -> Targets.isValidTarget(player, e));
        return hit != null && hit.getHitEntity() instanceof LivingEntity target ? target : null;
    }
}
