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
                ability("Shoot", "Fires 3 stronger arrows that bounce between players"),
                ability("Passive", "Shots have a 10% chance to swap around the hit player's hotbar")
        ));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
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
