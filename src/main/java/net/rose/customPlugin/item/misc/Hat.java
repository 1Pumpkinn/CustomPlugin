package net.rose.customPlugin.item.misc;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class Hat {

    public static final TextColor COLOR = TextColor.color(0xC03030);

    /** A music disc is a flat round sprite, like the Zeus Disc; the pack model replaces how it looks. */
    private static final Material BASE = Material.MUSIC_DISC_CAT;

    private final NamespacedKey key;

    public Hat(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "hat");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(BASE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Old Man's Hat", COLOR).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                ability("Right-click / Offhand", "Throw the hat down: it bounces anyone who lands on it for 2.5s"),
                ability("Crouch + Right-click / Offhand", "Throw the hat: if it hits someone it sticks to them and spins like a razor")
        ));
        meta.setMaxStackSize(1);
        meta.setItemModel(key); // texture pack model: customplugin:<name>
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);

        // No jukebox use, and no music disc line in the tooltip.
        item.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE);
        return item;
    }

    public boolean isHat(ItemStack item) {
        // Type first: this runs every tick for every online player, and getItemMeta() copies the meta.
        if (item == null || item.getType() != BASE || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static Component ability(String trigger, String description) {
        return Component.text(trigger, COLOR)
                .append(Component.text(" - " + description, NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }
}
