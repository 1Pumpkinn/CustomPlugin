package net.rose.customPlugin.item.misc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
        ItemStack item = new ItemStack(Material.RED_DYE, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Kill Token", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Dropped by a player", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack create() {
        return create(1);
    }

    /** How many kill tokens are in the player's main inventory (hotbar and storage, not the offhand or armor). */
    public int count(PlayerInventory inventory) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (isKillToken(stack)) total += stack.getAmount();
        }
        return total;
    }

    /** Takes that many kill tokens from the main inventory. Returns false, taking nothing, if there aren't enough. */
    public boolean take(PlayerInventory inventory, int amount) {
        if (amount <= 0) return true;
        if (count(inventory) < amount) return false;

        ItemStack[] contents = inventory.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (!isKillToken(stack)) continue;

            int removed = Math.min(left, stack.getAmount());
            left -= removed;
            if (removed == stack.getAmount()) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - removed);
            }
        }
        inventory.setStorageContents(contents);
        return true;
    }

    public boolean isKillToken(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}