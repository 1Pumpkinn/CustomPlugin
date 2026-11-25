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

public final class FrostSpear {

    private final NamespacedKey key;

    public FrostSpear(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "frost_spear");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(spearMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Frost Spear", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                ability("Offhand", "Dash forward, freezing and stunning every player you dash into"),
                ability("Passive", "Stabbing a player freezes them")
        ));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isFrostSpear(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Netherite spear (1.21.11+), falling back to a trident on versions without spears. */
    private static Material spearMaterial() {
        Material spear = Material.matchMaterial("NETHERITE_SPEAR");
        return spear != null ? spear : Material.TRIDENT;
    }

    private static Component ability(String trigger, String description) {
        return Component.text(trigger, NamedTextColor.AQUA)
                .append(Component.text(" - " + description, NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }
}
