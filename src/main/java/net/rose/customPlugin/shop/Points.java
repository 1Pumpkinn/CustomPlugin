package net.rose.customPlugin.shop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Every player's points, kept in plugins/CustomPlugin/points.yml. Balances live in memory and are written to disk
 * every 30 seconds when something changed, and on shutdown.
 */
public final class Points {

    private static final long SAVE_INTERVAL_TICKS = 20L * 30;

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Integer> balances = new HashMap<>();
    private final BukkitTask saveTask;
    private boolean dirty;

    public Points(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "points.yml");
        load();
        this.saveTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::saveIfDirty, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
    }

    public int get(UUID player) {
        return balances.getOrDefault(player, 0);
    }

    /** Sets the balance (never below 0) and returns it. */
    public int set(UUID player, int amount) {
        int value = Math.max(0, amount);
        balances.put(player, value);
        dirty = true;
        return value;
    }

    /** Adds points (a negative amount takes them, down to 0) and returns the new balance. */
    public int add(UUID player, int amount) {
        long sum = (long) get(player) + amount;
        return set(player, (int) Math.max(0L, Math.min(Integer.MAX_VALUE, sum)));
    }

    /** Takes exactly {@code amount} points if the player has that many. Returns false (and takes nothing) if not. */
    public boolean spend(UUID player, int amount) {
        if (amount < 0 || get(player) < amount) return false;
        set(player, get(player) - amount);
        return true;
    }

    public void shutdown() {
        saveTask.cancel();
        saveIfDirty();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), Math.max(0, yaml.getInt(key)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid entry in points.yml: " + key);
            }
        }
    }

    private void saveIfDirty() {
        if (!dirty) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : balances.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save points.yml", e);
        }
    }
}
