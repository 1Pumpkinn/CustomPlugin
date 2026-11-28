package net.rose.customPlugin.item.bow;

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

public final class HuntersBow {

    public static final TextColor COLOR = TextColor.color(0xE8890C);

    private final NamespacedKey key;

    public HuntersBow(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "hunters_bow");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Hunter's Bow", COLOR)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                ability("Offhand", "Highlight every player and mob near you, so they can be seen through walls"),
                ability("Passive", "Players and mobs hit by your arrows are highlighted")
        ));
        meta.setItemModel(key); // texture pack model: customplugin:<name>
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        MaxEnchants.apply(item); // weapons always come fully enchanted
        return item;
    }

    public boolean isHuntersBow(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static Component ability(String trigger, String description) {
        return Component.text(trigger, COLOR)
                .append(Component.text(" - " + description, NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }
}
