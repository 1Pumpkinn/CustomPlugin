package net.rose.customPlugin.item.disc;

import io.papermc.paper.datacomponent.DataComponentTypes;
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

public final class ZeusDisc {

    private final NamespacedKey key;

    public ZeusDisc(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "zeus_disc");
    }

    public ItemStack create() {
        // A music disc is a flat round sprite, so it reads as a disc while it spins (no resource pack needed).
        ItemStack item = new ItemStack(Material.MUSIC_DISC_13);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Zeus Disc", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                ability("Right-click", "Throw the disc. It ricochets off walls and flies back to you"),
                ability("Offhand", "Prime your next throw to strike everything it hits with lightning"),
                ability("Passive", "Hitting something near another target arcs sparks to nearby players and mobs for half a heart")
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.setItemModel(key); // texture pack model: customplugin:<name>
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);

        // No jukebox use, and no "C418 - 13" line in the tooltip.
        item.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE);
        return item;
    }

    public boolean isZeusDisc(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static Component ability(String trigger, String description) {
        return Component.text(trigger, NamedTextColor.YELLOW)
                .append(Component.text(" - " + description, NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }
}
