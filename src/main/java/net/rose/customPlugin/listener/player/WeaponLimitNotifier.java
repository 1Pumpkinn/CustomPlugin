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
            List<Component> overNames = new ArrayList<>();
            List<Component> duplicateNames = new ArrayList<>();
            for (WeaponLimit.Carried carried : limit.disabledCarried(player).values()) {
                Legendaries.Weapon weapon = carried.weapon();
                Component name = Component.text(weapon.name(), weapon.color());
                if (carried.duplicate()) {
                    ids.add("duplicate:" + weapon.id());
                    duplicateNames.add(name);
                } else {
                    ids.add(weapon.id());
                    overNames.add(name);
                }
            }

            List<String> before = notified.getOrDefault(player.getUniqueId(), List.of());
            if (ids.equals(before)) continue;

            if (ids.isEmpty()) {
                notified.remove(player.getUniqueId());
                player.sendMessage(Component.text("Weapon limit: all your weapons are active again.", NamedTextColor.GREEN));
                continue;
            }
            notified.put(player.getUniqueId(), ids);
            if (!overNames.isEmpty()) {
                player.sendMessage(Component.text("Weapon limit (" + maxPoints + (maxPoints == 1 ? " point" : " points")
                        + "): ", NamedTextColor.RED)
                        .append(join(overNames))
                        .append(Component.text(overNames.size() == 1 ? " is disabled." : " are disabled.", NamedTextColor.RED)));
            }
            if (!duplicateNames.isEmpty()) {
                player.sendMessage(Component.text("You can't use two of the same weapon: ", NamedTextColor.RED)
                        .append(join(duplicateNames))
                        .append(Component.text(duplicateNames.size() == 1 ? " (the extra copy) is disabled." : " (the extra copies) are disabled.",
                                NamedTextColor.RED)));
            }
        }
    }

    private static Component join(List<Component> names) {
        return Component.join(JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)), names);
    }
}
