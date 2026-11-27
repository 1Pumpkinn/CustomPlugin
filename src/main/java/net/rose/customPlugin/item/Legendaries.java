package net.rose.customPlugin.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** Every custom weapon in the plugin, so something can ask which of them a player is carrying. */
public final class Legendaries {

    private record Entry(NamespacedKey key, String name, TextColor color) {
    }

    private final List<Entry> entries;

    public Legendaries(JavaPlugin plugin) {
        this.entries = List.of(
                entry(plugin, "tide_blade", "Tide Blade", NamedTextColor.AQUA),
                entry(plugin, "volcanic_axe", "Volcanic Axe", NamedTextColor.GOLD),
                entry(plugin, "frost_spear", "Frost Spear", NamedTextColor.AQUA),
                entry(plugin, "metalic_mace", "Metalic Mace", NamedTextColor.GRAY),
                entry(plugin, "trickster_bow", "Trickster Bow", NamedTextColor.LIGHT_PURPLE),
                entry(plugin, "zeus_disc", "Zeus Disc", NamedTextColor.YELLOW),
                entry(plugin, "petal_blade", "Petal Blade", PetalBlade.PINK),
                entry(plugin, "bloodthirst_scythe", "Bloodthirst Scythe", BloodthirstScythe.BLOOD),
                entry(plugin, "hat", "Hat", Hat.COLOR));
    }

    /** Colored names of the distinct custom weapons anywhere in the player's inventory, armor and offhand included. */
    public List<Component> carriedBy(Player player) {
        List<Component> found = new ArrayList<>();
        for (Entry entry : entries) {
            for (ItemStack stack : player.getInventory().getContents()) {
                if (has(stack, entry.key())) {
                    found.add(Component.text(entry.name(), entry.color()));
                    break;
                }
            }
        }
        return found;
    }

    private static boolean has(ItemStack stack, NamespacedKey key) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static Entry entry(JavaPlugin plugin, String id, String name, TextColor color) {
        return new Entry(new NamespacedKey(plugin, id), name, color);
    }
}
