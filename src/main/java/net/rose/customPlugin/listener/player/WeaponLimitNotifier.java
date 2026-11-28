package net.rose.customPlugin.listener.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.item.Legendaries;
import net.rose.customPlugin.item.WeaponLimit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Once a second, tells each player when the set of weapons that is disabled for them changes (and only then, so
 * nobody is spammed).
 */
public final class WeaponLimitNotifier {

    private static final long CHECK_INTERVAL_TICKS = 20L;

    private final JavaPlugin plugin;
    private final WeaponLimit limit;
    /** The weapons (by id) each online player was last told are disabled. */
    private final Map<UUID, List<String>> notified = new HashMap<>();
    private final BukkitTask task;

    public WeaponLimitNotifier(JavaPlugin plugin, WeaponLimit limit) {
        this.plugin = plugin;
        this.limit = limit;
        // Always scheduled: enabled / max-points are read from the config each time, so /customplugin reload applies them.
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkAll,
                CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    /** The player left: forget what they were told. */
    public void forget(UUID player) {
        notified.remove(player);
    }

    public void shutdown() {
        task.cancel();
        notified.clear();
    }

    private void checkAll() {
        if (!limit.isEnabled()) {
            notified.clear();
            return;
        }
        int maxPoints = limit.maxPoints();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            List<String> ids = new ArrayList<>();
            List<Component> names = new ArrayList<>();
            for (Legendaries.Weapon weapon : limit.disabledSlots(player).values()) {
                ids.add(weapon.id());
                names.add(Component.text(weapon.name(), weapon.color()));
            }

            List<String> before = notified.getOrDefault(player.getUniqueId(), List.of());
            if (ids.equals(before)) continue;

            if (ids.isEmpty()) {
                notified.remove(player.getUniqueId());
                player.sendMessage(Component.text("Weapon limit: all your weapons are active again.", NamedTextColor.GREEN));
            } else {
                notified.put(player.getUniqueId(), ids);
                player.sendMessage(Component.text("Weapon limit (" + maxPoints + (maxPoints == 1 ? " point" : " points")
                        + "): ", NamedTextColor.RED)
                        .append(Component.join(JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)), names))
                        .append(Component.text(ids.size() == 1 ? " is disabled." : " are disabled.", NamedTextColor.RED)));
            }
        }
    }
}
