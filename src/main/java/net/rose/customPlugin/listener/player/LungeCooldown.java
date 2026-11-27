package net.rose.customPlugin.listener.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 10s cooldown between spear lunges (the Lunge enchantment), for servers without Paper's EntityLungeEvent
 * (1.21.11). The jab is an arm swing, so the swing is the trigger:
 *
 * <ul>
 *   <li>Swinging a Lunge spear off cooldown starts the cooldown. The time left counts down on the action bar
 *       (the hotbar) until it is ready again.</li>
 *   <li>Swinging during the cooldown cancels the swing, undoes the hunger the lunge would cost, and zeroes the
 *       horizontal velocity the lunge gives for as long as the cooldown runs (anything slower than the lunge, and
 *       anything right after taking a hit, is left alone).</li>
 * </ul>
 */
public final class LungeCooldown implements Listener {

    private static final long LUNGE_COOLDOWN_MS = 10_000;
    /** Knockback right after being hit is never wiped. */
    private static final long KNOCKBACK_GRACE_MS = 500;
    private static final long DISPLAY_INTERVAL_TICKS = 2;

    private final JavaPlugin plugin;
    private final Map<UUID, Long> endsAt = new HashMap<>();
    private final Map<UUID, BukkitTask> display = new HashMap<>();
    private final Map<UUID, BukkitTask> suppression = new HashMap<>();
    private final Map<UUID, Double> thresholds = new HashMap<>();
    private final Map<UUID, Long> knockbackGraceUntil = new HashMap<>();
    private boolean loggedFirstSwing = false;

    public LungeCooldown(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Lunge cooldown active (" + LUNGE_COOLDOWN_MS / 1000 + "s)");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        Player player = event.getPlayer();
        int level = lungeLevel(player.getInventory().getItemInMainHand());
        if (level <= 0) return;

        if (!loggedFirstSwing) {
            loggedFirstSwing = true;
            plugin.getLogger().info("Lunge spear swing detected for " + player.getName() + " (lunge level " + level + "): the cooldown is hooked up");
        }

        long remaining = remainingMillis(player);
        if (remaining <= 0) {
            start(player);
            return;
        }

        event.setCancelled(true);
        startSuppression(player, level);

        // The lunge's hunger cost can still go through: put it back.
        int food = player.getFoodLevel();
        float saturation = player.getSaturation();
        for (long delay = 1; delay <= 2; delay++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.setFoodLevel(food);
                player.setSaturation(saturation);
            }, delay);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            knockbackGraceUntil.put(player.getUniqueId(), System.currentTimeMillis() + KNOCKBACK_GRACE_MS);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cancel(display.remove(id));
        cancel(suppression.remove(id));
        thresholds.remove(id);
        knockbackGraceUntil.remove(id);
        endsAt.remove(id);
    }

    // ---------------------------------------------------------------- Cooldown + action bar countdown

    private void start(Player player) {
        UUID id = player.getUniqueId();
        endsAt.put(id, System.currentTimeMillis() + LUNGE_COOLDOWN_MS);

        cancel(display.remove(id));
        display.put(id, plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancel(display.remove(id));
                return;
            }
            long left = remainingMillis(player);
            if (left <= 0) {
                player.sendActionBar(Component.text("Lunge ready", NamedTextColor.GREEN));
                cancel(display.remove(id));
                return;
            }
            player.sendActionBar(Component.text(
                    String.format(Locale.ROOT, "Lunge cooldown - %.1fs", left / 1000.0), NamedTextColor.RED));
        }, 0L, DISPLAY_INTERVAL_TICKS));
    }

    // ---------------------------------------------------------------- Blocking the lunge

    /** While the cooldown runs, wipe any horizontal speed as fast as a lunge (not normal walking or sprinting). */
    private void startSuppression(Player player, int level) {
        UUID id = player.getUniqueId();
        thresholds.put(id, thresholdForLevel(level));
        if (suppression.containsKey(id)) return;

        suppression.put(id, plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || remainingMillis(player) <= 0) {
                cancel(suppression.remove(id));
                thresholds.remove(id);
                return;
            }
            if (player.isGliding() || player.isInsideVehicle()) return;

            Long grace = knockbackGraceUntil.get(id);
            if (grace != null && System.currentTimeMillis() < grace) return;

            Vector velocity = player.getVelocity();
            double horizontal = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
            if (horizontal > thresholds.getOrDefault(id, 1.0)) {
                player.setVelocity(new Vector(0, velocity.getY(), 0));
            }
        }, 0L, 1L));
    }

    private static double thresholdForLevel(int level) {
        return switch (level) {
            case 1 -> 0.3;
            case 2 -> 0.7;
            default -> 1.0;
        };
    }

    // ---------------------------------------------------------------- Helpers

    /** Lunge level of the item, found by enchantment name so it works however the server names the registry. */
    private static int lungeLevel(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return 0;
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            if (entry.getKey().getKey().getKey().toLowerCase(Locale.ROOT).contains("lunge")) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private long remainingMillis(Player player) {
        Long end = endsAt.get(player.getUniqueId());
        if (end == null) return 0;

        long left = end - System.currentTimeMillis();
        if (left <= 0) {
            endsAt.remove(player.getUniqueId());
            return 0;
        }
        return left;
    }

    private static void cancel(BukkitTask task) {
        if (task != null) task.cancel();
    }
}