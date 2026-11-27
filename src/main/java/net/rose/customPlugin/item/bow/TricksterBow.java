package net.rose.customPlugin.item.bow;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.rose.customPlugin.util.MaxEnchants;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class TricksterBow {

    private final NamespacedKey key;

    public TricksterBow(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "trickster_bow");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Trickster Bow", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                ability("Offhand", "Prime your next shot to fire 3 stronger arrows that bounce between players and mobs"),
                ability("Passive", "Shots have a 10% chance to swap the hit player's held item with another hotbar slot")
        ));
        meta.setItemModel(key); // texture pack model: customplugin:<name>
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        MaxEnchants.apply(item); // weapons always come fully enchanted
        return item;
    }

    public boolean isTricksterBow(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static Component ability(String trigger, String description) {
        return Component.text(trigger, NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(" - " + description, NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }
}
