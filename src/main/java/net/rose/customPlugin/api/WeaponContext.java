package net.rose.customPlugin.api;

import net.rose.customPlugin.util.CooldownBars;
import net.rose.customPlugin.util.DeathMessages;
import org.bukkit.plugin.java.JavaPlugin;

/** The shared services a weapon's listener needs. Handed to {@link CustomWeapon#createListener(WeaponContext)}. */
public record WeaponContext(JavaPlugin plugin, CooldownBars cooldowns, DeathMessages deaths) {
}
