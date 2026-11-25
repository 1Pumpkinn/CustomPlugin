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

public final class KillToken {

    private final NamespacedKey key;

    public KillToken(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "kill_token");
    }

    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Kill Token", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Dropped by a fallen player", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack create() {
        return create(1);
    }

    public boolean isKillToken(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
