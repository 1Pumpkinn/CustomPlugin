package net.rose.customPlugin.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Upgrades an older config.yml to the current layout. The old file is kept as config-backup-v1.yml, a fresh
 * commented config.yml is written, and every value the server owner had set is carried over (the old
 * {@code weapon-limit.weapon-tiers.<id>} and {@code shop.<id>} entries become {@code weapons.<id>.tier / price}).
 */
public final class ConfigMigrator {

    public static final int CURRENT_VERSION = 2;

    private static final Set<String> SHOP_NON_WEAPON_KEYS = Set.of("default-price");

    private ConfigMigrator() {
    }

    public static void run(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        File file = new File(plugin.getDataFolder(), "config.yml");
        // Read the raw file, without the defaults that come with the jar.
        YamlConfiguration old = YamlConfiguration.loadConfiguration(file);
        if (old.getInt("config-version", 1) >= CURRENT_VERSION) return;

        try {
            Files.copy(file.toPath(), new File(plugin.getDataFolder(), "config-backup-v1.yml").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not back up the old config.yml, leaving it as it is: " + e.getMessage());
            return;
        }

        Map<String, Object> carried = new LinkedHashMap<>();
        for (String key : old.getKeys(true)) {
            if (old.isConfigurationSection(key)) continue;
            carried.put(key, old.get(key));
        }

        plugin.saveResource("config.yml", true);
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        for (Map.Entry<String, Object> entry : carried.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key.startsWith("weapon-limit.weapon-tiers.")) {
                config.set("weapons." + key.substring("weapon-limit.weapon-tiers.".length()) + ".tier", value);
            } else if (key.startsWith("shop.weapon-prices.")) {
                config.set("weapons." + key.substring("shop.weapon-prices.".length()) + ".price", value);
            } else if (key.startsWith("shop.") && key.indexOf('.', 5) < 0 && !SHOP_NON_WEAPON_KEYS.contains(key.substring(5))) {
                config.set("weapons." + key.substring(5) + ".price", value); // old "shop.<id>: price"
            } else if (!key.equals("config-version") && config.contains(key)) {
                config.set(key, value);
            }
        }
        config.set("config-version", CURRENT_VERSION);
        plugin.saveConfig();
        plugin.getLogger().info("Upgraded config.yml to version " + CURRENT_VERSION
                + " (the old one is saved as config-backup-v1.yml).");
    }
}
