package net.rose.customPlugin.item;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A weapon's tier, weapon points and shop price, read from {@code weapons.<id>} in config.yml every time they are
 * asked for, so a {@code /customplugin reload} is all it takes for an edit to show up in game.
 */
public final class WeaponSettings {

    private static final int DEFAULT_PRICE = 5;

    private final JavaPlugin plugin;

    public WeaponSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** {@code weapons.<id>.tier}, or {@code weapon-limit.default-tier} when the weapon has none. */
    public int tier(String id) {
        FileConfiguration config = plugin.getConfig();
        int fallback = Math.max(1, config.getInt("weapon-limit.default-tier", 1));
        return Math.max(1, config.getInt("weapons." + id + ".tier", fallback));
    }

    /** What a tier costs in weapon points ({@code weapon-limit.tier-points.<tier>}). */
    public int pointsForTier(int tier) {
        return Math.max(0, plugin.getConfig().getInt("weapon-limit.tier-points." + tier, 1));
    }

    /** {@code weapons.<id>.points} if set, otherwise what the weapon's tier costs. */
    public int points(String id) {
        FileConfiguration config = plugin.getConfig();
        if (config.isSet("weapons." + id + ".points")) {
            return Math.max(0, config.getInt("weapons." + id + ".points"));
        }
        return pointsForTier(tier(id));
    }

    /** {@code weapons.<id>.price} if set, otherwise {@code shop.tier-prices.<tier>}, otherwise {@code shop.default-price}. */
    public int price(String id) {
        FileConfiguration config = plugin.getConfig();
        int tierPrice = config.getInt("shop.tier-prices." + tier(id), config.getInt("shop.default-price", DEFAULT_PRICE));
        return Math.max(1, config.getInt("weapons." + id + ".price", tierPrice));
    }
}
