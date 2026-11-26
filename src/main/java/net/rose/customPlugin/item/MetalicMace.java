package net.rose.customPlugin.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class MetalicMace {

    private final NamespacedKey key;

    public MetalicMace(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "metalic_mace");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Metalic Mace", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                ability("Offhand", "Pull a player or mob in with a chain"),
                ability("Chain", "The pulled target is slowed for 5s and can't jump or use wind charges")
        ));
        meta.setItemModel(key); // texture pack model: customplugin:<name>
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMetalicMace(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static Component ability(String trigger, String description) {
        return Component.text(trigger, NamedTextColor.WHITE)
                .append(Component.text(" - " + description, NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }
}
