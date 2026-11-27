package net.rose.customPlugin.item.scythe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.rose.customPlugin.util.MaxEnchants;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class BloodthirstScythe {

    public static final TextColor BLOOD = TextColor.color(0xC4121F);

    private final JavaPlugin plugin;
    private final NamespacedKey key;

    public BloodthirstScythe(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "bloodthirst_scythe");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Bloodthirst Scythe", BLOOD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                ability("Hit", "Store 0.3 hearts per hit (max 4)"),
                ability("Offhand", "Vampire sweep: slash the arc in front of you for 2.5 hearts of true damage and steal it back as health"),
                ability("Crouch + Offhand", "Spend every stored heart to heal yourself (30s cooldown, no blood is stored while it recharges)")
        ));

        meta.setItemModel(key); // texture pack model: customplugin:<name>
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        MaxEnchants.apply(item); // weapons always come fully enchanted
        return item;
    }

    public boolean isBloodthirstScythe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static Component ability(String trigger, String description) {
        return Component.text(trigger, BLOOD)
                .append(Component.text(" - " + description, NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }
}
