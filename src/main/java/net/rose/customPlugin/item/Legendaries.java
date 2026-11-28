package net.rose.customPlugin.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.rose.customPlugin.item.misc.Hat;
import net.rose.customPlugin.item.scythe.BloodthirstScythe;
import net.rose.customPlugin.item.sword.PetalBlade;
import net.rose.customPlugin.item.trident.TempestTrident;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** Every custom weapon in the plugin, so something can ask which of them a player is carrying. */
public final class Legendaries {

    /** A custom weapon: {@code id} is its /weapon name (also the key in config.yml), {@code color} its signature color. */
    public record Weapon(String id, String name, TextColor color) {
    }

    private record Entry(NamespacedKey key, Weapon weapon) {
    }

    private final List<Entry> entries;

    public Legendaries(JavaPlugin plugin) {
        this.entries = List.of(
                entry(plugin, "tide_blade", "tideblade", "Tide Blade", NamedTextColor.AQUA),
                entry(plugin, "volcanic_axe", "volcanicaxe", "Volcanic Axe", NamedTextColor.GOLD),
                entry(plugin, "frost_spear", "frostspear", "Frost Spear", NamedTextColor.AQUA),
                entry(plugin, "metalic_mace", "metalicmace", "Metalic Mace", NamedTextColor.GRAY),
                entry(plugin, "trickster_bow", "tricksterbow", "Trickster Bow", NamedTextColor.LIGHT_PURPLE),
                entry(plugin, "zeus_disc", "zeusdisc", "Zeus Disc", NamedTextColor.YELLOW),
                entry(plugin, "petal_blade", "petalblade", "Petal Blade", PetalBlade.PINK),
                entry(plugin, "bloodthirst_scythe", "bloodthirstscythe", "Bloodthirst Scythe", BloodthirstScythe.BLOOD),
                entry(plugin, "tempest_trident", "tempesttrident", "Tempest Trident", TempestTrident.COLOR),
                entry(plugin, "hat", "hat", "Hat", Hat.COLOR));
    }

    /** Colored names of the distinct custom weapons anywhere in the player's inventory, armor and offhand included. */
    public List<Component> carriedBy(Player player) {
        List<Component> found = new ArrayList<>();
        for (Entry entry : entries) {
            for (ItemStack stack : player.getInventory().getContents()) {
                if (has(stack, entry.key())) {
                    found.add(Component.text(entry.weapon().name(), entry.weapon().color()));
                    break;
                }
            }
        }
        return found;
    }

    /** Which custom weapon this stack is, or null if it is not one. */
    public Weapon identify(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) return null;
        for (Entry entry : entries) {
            if (has(stack, entry.key())) return entry.weapon();
        }
        return null;
    }

    private static boolean has(ItemStack stack, NamespacedKey key) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static Entry entry(JavaPlugin plugin, String key, String id, String name, TextColor color) {
        return new Entry(new NamespacedKey(plugin, key), new Weapon(id, name, color));
    }
}
