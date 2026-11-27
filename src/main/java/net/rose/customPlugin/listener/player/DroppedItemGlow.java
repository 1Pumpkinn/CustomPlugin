package net.rose.customPlugin.listener.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * Dropped CustomPlugin items (weapons, kill tokens) glow and show their name above them, so they are easy to
 * find on the ground. It's the real item entity that is changed: no display entities are spawned.
 */
public final class DroppedItemGlow implements Listener {

    private final String namespace;

    public DroppedItemGlow(JavaPlugin plugin) {
        this.namespace = plugin.getName().toLowerCase(Locale.ROOT);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        ItemStack stack = entity.getItemStack();
        if (!isPluginItem(stack)) return;

        entity.setGlowing(true);
        entity.customName(label(stack));
        entity.setCustomNameVisible(true);
    }

    /** Every item this plugin creates carries a persistent data tag under the plugin's namespace. */
    private boolean isPluginItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) return false;
        for (NamespacedKey key : stack.getItemMeta().getPersistentDataContainer().getKeys()) {
            if (key.getNamespace().equals(namespace)) return true;
        }
        return false;
    }

    private static Component label(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        Component name = meta.hasDisplayName()
                ? meta.displayName()
                : Component.translatable(stack.translationKey());
        if (stack.getAmount() > 1) {
            name = name.append(Component.text(" x" + stack.getAmount(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return name;
    }
}
