package net.rose.customPlugin.util;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Puts every enchantment the item can take on it at its maximum level.
 *
 * <p>Curses and Knockback are skipped, and when two enchantments conflict (Sharpness/Smite, Infinity/Mending,
 * Loyalty/Riptide, Density/Breach...) the one listed first in {@link #PRIORITY} wins.
 */
public final class MaxEnchants {

    /** Preferred enchantments, in order. Anything the item can take that is not listed comes after these. */
    private static final List<String> PRIORITY = List.of(
            "sharpness", "density", "power", "impaling", "lunge",
            "fire_aspect", "flame", "punch", "looting", "sweeping_edge",
            "wind_burst", "breach", "loyalty", "channeling",
            "efficiency", "unbreaking", "fortune", "infinity", "mending");

    /** Never added, even though the item could take them. */
    private static final Set<String> SKIPPED = Set.of("knockback");

    private MaxEnchants() {
    }

    public static void apply(ItemStack item) {
        Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);

        Set<Enchantment> ordered = new LinkedHashSet<>();
        for (String name : PRIORITY) {
            Enchantment enchantment = registry.get(NamespacedKey.minecraft(name));
            if (enchantment != null) ordered.add(enchantment);
        }
        for (Enchantment enchantment : registry) {
            ordered.add(enchantment);
        }

        List<Enchantment> applied = new ArrayList<>();
        for (Enchantment enchantment : ordered) {
            String name = enchantment.getKey().getKey();
            if (name.endsWith("_curse") || SKIPPED.contains(name)) continue;
            if (!enchantment.canEnchantItem(item)) continue;
            boolean conflicts = false;
            for (Enchantment other : applied) {
                if (enchantment.conflictsWith(other)) {
                    conflicts = true;
                    break;
                }
            }
            if (conflicts) continue;
            item.addUnsafeEnchantment(enchantment, enchantment.getMaxLevel());
            applied.add(enchantment);
        }
    }
}
