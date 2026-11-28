package net.rose.customPlugin.item;

import net.rose.customPlugin.item.axe.VolcanicAxe;
import net.rose.customPlugin.item.bow.TricksterBow;
import net.rose.customPlugin.item.disc.ZeusDisc;
import net.rose.customPlugin.item.mace.MetalicMace;
import net.rose.customPlugin.item.misc.Hat;
import net.rose.customPlugin.item.scythe.BloodthirstScythe;
import net.rose.customPlugin.item.spear.FrostSpear;
import net.rose.customPlugin.item.sword.PetalBlade;
import net.rose.customPlugin.item.sword.TideBlade;
import net.rose.customPlugin.item.trident.TempestTrident;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Every giveable weapon by its command name. Used by /weapon and the lore refresher. */
public final class WeaponRegistry {

    private final Map<String, Supplier<ItemStack>> weapons = new LinkedHashMap<>();

    public WeaponRegistry(TideBlade tideBlade, VolcanicAxe volcanicAxe, FrostSpear frostSpear, MetalicMace metalicMace,
                          TricksterBow tricksterBow, ZeusDisc zeusDisc, PetalBlade petalBlade,
                          BloodthirstScythe bloodthirstScythe, TempestTrident tempestTrident, Hat hat) {
        register("tideblade", tideBlade::create);
        register("volcanicaxe", volcanicAxe::create);
        register("frostspear", frostSpear::create);
        register("metalicmace", metalicMace::create);
        register("tricksterbow", tricksterBow::create);
        register("zeusdisc", zeusDisc::create);
        register("petalblade", petalBlade::create);
        register("bloodthirstscythe", bloodthirstScythe::create);
        register("tempesttrident", tempestTrident::create);
        register("hat", hat::create);
        // Register future weapons here: register("name", item::create);
    }

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
