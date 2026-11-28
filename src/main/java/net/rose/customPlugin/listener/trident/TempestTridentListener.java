package net.rose.customPlugin.listener.trident;

import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.effect.TempestSpinEffect;
import net.rose.customPlugin.item.trident.TempestTrident;
import net.rose.customPlugin.util.CooldownBars;
import net.rose.customPlugin.util.Targets;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TempestTridentListener implements Listener {

    private static final long THROW_COOLDOWN_MS = 30_000;
    private static final String THROW_ID = "tempest_throw";
    private static final String LABEL = "Tempest";
    /** A trident that never reports a hit (void, despawn, unloaded chunk) is forgotten after this long. */
    private static final long EMPOWERED_TIMEOUT_MS = 5 * 60_000L;

    private record Empowered(UUID thrower, long at) {
    }

    private final JavaPlugin plugin;
    private final TempestTrident trident;
    private final CooldownBars cooldowns;
    private final TempestSpinEffect spin;

    private final Set<UUID> armed = new HashSet<>();
    /** Tridents in flight that carry the ability: trident entity id -> who threw it. */
    private final Map<UUID, Empowered> empowered = new HashMap<>();

    public TempestTridentListener(JavaPlugin plugin, TempestTrident trident, CooldownBars cooldowns,
                                  TempestSpinEffect spin) {
        this.plugin = plugin;
        this.trident = trident;
        this.cooldowns = cooldowns;
        this.spin = spin;
    }

    /** Offhand key (F) while holding the Tempest Trident primes the next throw. The swap itself is always cancelled. */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!trident.isTempestTrident(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);
        if (cooldowns.isOnCooldown(player, THROW_ID)) return; // boss bar already shows the time left
        if (!armed.add(player.getUniqueId())) return;

        cooldowns.prime(player, THROW_ID, LABEL, BossBar.Color.BLUE);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.6f);
    }

    /** The next Tempest Trident the player throws after priming carries the ability. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident thrown)) return;
        if (!(thrown.getShooter() instanceof Player player)) return;
        if (!trident.isTempestTrident(thrown.getItemStack())) return;
        if (!armed.remove(player.getUniqueId())) return;

        cooldowns.unprime(player, THROW_ID);
        cooldowns.start(player, THROW_ID, LABEL, BossBar.Color.BLUE, THROW_COOLDOWN_MS);
        long now = System.currentTimeMillis();
        empowered.values().removeIf(entry -> now - entry.at() > EMPOWERED_TIMEOUT_MS);
        empowered.put(thrown.getUniqueId(), new Empowered(player.getUniqueId(), now));
    }

    /** Hitting a player or mob starts the spin and hands the trident back; a miss just uses up the charge. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident thrown)) return;
        Empowered entry = empowered.remove(thrown.getUniqueId());
        if (entry == null) return;

        Player thrower = plugin.getServer().getPlayer(entry.thrower());
        Entity hit = event.getHitEntity();
        if (thrower == null || !(hit instanceof LivingEntity victim) || !Targets.isValidTarget(thrower, victim)) return;

        thrown.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        ItemStack returned = thrown.getItemStack().clone();

        // One tick later, after vanilla has finished handling the hit.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (thrown.isValid()) thrown.remove();
            if (!thrower.isOnline()) return;

            spin.start(thrower, victim);
            if (thrower.getGameMode() == GameMode.CREATIVE) return; // creative throws never used the item up
            for (ItemStack leftover : thrower.getInventory().addItem(returned).values()) {
                thrower.getWorld().dropItemNaturally(thrower.getLocation(), leftover);
            }
        });
    }

    /** Dropping the weapon cancels a primed ability, so its boss bar doesn't stay behind. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!trident.isTempestTrident(event.getItemDrop().getItemStack())) return;
        Player player = event.getPlayer();
        if (armed.remove(player.getUniqueId())) cooldowns.unprime(player, THROW_ID);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        armed.remove(event.getEntity().getUniqueId());
        cooldowns.unprime(event.getEntity(), THROW_ID);
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
}
