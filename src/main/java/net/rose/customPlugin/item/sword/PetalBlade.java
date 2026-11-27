package net.rose.customPlugin.item.sword;

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

public final class PetalBlade {

    public static final TextColor PINK = TextColor.color(0xFF8FC8);

    private final NamespacedKey key;

    public PetalBlade(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "petal_blade");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Petal Blade", PINK)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                ability("Offhand", "Summon 3 petals that circle you"),
                ability("Right-click", "Throw a petal for 1 heart of damage")
        ));
        meta.setItemModel(key); // texture pack model: customplugin:<name>
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        MaxEnchants.apply(item); // weapons always come fully enchanted
        return item;
    }

    public boolean isPetalBlade(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static Component ability(String trigger, String description) {
        return Component.text(trigger, PINK)
                .append(Component.text(" - " + description, NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }
}
