package net.rose.customPlugin.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Every custom weapon in the plugin (added by {@code WeaponManager.register}), so something can ask which of them a player is carrying. */
public final class Legendaries {

    /** A custom weapon: {@code id} is its /weapon name (also the key in config.yml), {@code color} its signature color. */
    public record Weapon(String id, String name, TextColor color) {
    }

    private record Entry(NamespacedKey key, Weapon weapon) {
    }

    private final List<Entry> entries;

    public Legendaries() {
        this.entries = new ArrayList<>();
    }

    /** Adds a weapon (done for you by WeaponManager.register). Every weapon added here counts for the weapon limit, death notice and dropped-item beacon. */
    public void register(NamespacedKey key, String id, String name, TextColor color) {
        entries.add(new Entry(key, new Weapon(id, name, color)));
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
}
