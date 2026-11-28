package net.rose.customPlugin.item;

import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Every giveable weapon by its command name (filled by WeaponManager.register). Used by /weapon, the shop and the lore refresher. */
public final class WeaponRegistry {

    private final Map<String, Supplier<ItemStack>> weapons = new LinkedHashMap<>();

    public void register(String id, Supplier<ItemStack> supplier) {
        weapons.put(id.toLowerCase(Locale.ROOT), supplier);
    }

    /** Command names in registration order. */
    public Set<String> ids() {
        return Collections.unmodifiableSet(weapons.keySet());
    }

    public boolean has(String id) {
        return weapons.containsKey(id.toLowerCase(Locale.ROOT));
    }

    /** A fresh copy of the weapon, or null if there is no weapon by that name. */
    public ItemStack create(String id) {
        Supplier<ItemStack> supplier = weapons.get(id.toLowerCase(Locale.ROOT));
        return supplier == null ? null : supplier.get();
    }
}
