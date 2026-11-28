package net.rose.customPlugin.api;

import net.rose.customPlugin.item.Legendaries;
import net.rose.customPlugin.item.WeaponRegistry;
import net.rose.customPlugin.util.CooldownBars;
import net.rose.customPlugin.util.DeathMessages;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Registers {@link CustomWeapon}s. One call does everything a new weapon needs:
 * <ol>
 *   <li>/weapon command, shop and lore refresher ({@link WeaponRegistry});</li>
 *   <li>weapon limit, legendary death notice, dropped-item beacon ({@link Legendaries});</li>
 *   <li>a {@code weapons.<id>} entry (tier, price) in config.yml if the server does not have one yet;</li>
 *   <li>the weapon's listener, if it has one.</li>
 * </ol>
 * Register weapons in onEnable before anything that scans for them (the dropped-item beacon, the shop).
 */
public final class WeaponManager {

    private final JavaPlugin plugin;
    private final Legendaries legendaries;
    private final WeaponRegistry registry;
    private final WeaponContext context;
    private final List<CustomWeapon> weapons = new ArrayList<>();

    public WeaponManager(JavaPlugin plugin, Legendaries legendaries, WeaponRegistry registry,
                         CooldownBars cooldowns, DeathMessages deaths) {
        this.plugin = plugin;
        this.legendaries = legendaries;
        this.registry = registry;
        this.context = new WeaponContext(plugin, cooldowns, deaths);
    }

    /** Wires the weapon in everywhere. Returns it, so {@code HuntersBow bow = weapons.register(new HuntersBow(this))} works. */
    public <T extends CustomWeapon> T register(T weapon) {
        String id = weapon.id();
        if (!id.equals(id.toLowerCase(Locale.ROOT)) || id.isBlank() || id.contains(" ")) {
            throw new IllegalArgumentException("Weapon id must be lower case with no spaces: '" + id + "'");
        }
        if (registry.has(id)) {
            throw new IllegalArgumentException("A weapon with the id '" + id + "' is already registered");
        }

        legendaries.register(weapon.key(), id, weapon.displayName(), weapon.color());
        registry.register(id, weapon::create);
        addConfigDefaults(weapon);

        Listener listener = weapon.createListener(context);
        if (listener != null) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
        weapons.add(weapon);
        return weapon;
    }

    /** Every weapon registered through this manager, in registration order. */
    public List<CustomWeapon> all() {
        return Collections.unmodifiableList(weapons);
    }

    /** Call from onDisable: lets each weapon end what it has running. One weapon failing does not stop the others. */
    public void shutdown() {
        for (CustomWeapon weapon : weapons) {
            try {
                weapon.onDisable();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Could not shut down " + weapon.id() + ": " + e);
            }
        }
    }

    /** Writes the weapon's tier and price into config.yml when the server has no entry for it yet. */
    private void addConfigDefaults(CustomWeapon weapon) {
        FileConfiguration config = plugin.getConfig();
        String base = "weapons." + weapon.id();
        boolean changed = false;
        if (!config.isSet(base + ".tier")) {
            config.set(base + ".tier", weapon.defaultTier());
            changed = true;
        }
        if (!config.isSet(base + ".price")) {
            config.set(base + ".price", weapon.defaultPrice());
            changed = true;
        }
        if (changed) plugin.saveConfig();
    }
}
