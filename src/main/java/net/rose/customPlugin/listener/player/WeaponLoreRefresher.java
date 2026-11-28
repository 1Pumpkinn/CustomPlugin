package net.rose.customPlugin.listener.player;

import net.kyori.adventure.text.Component;
import net.rose.customPlugin.item.Legendaries;
import net.rose.customPlugin.item.WeaponRegistry;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Objects;

/**
 * Lore is stored on the item when it is created, so weapons made before a description changed would keep showing the
 * old one forever. This gives them the current description when their owner joins or picks them up. Only the lore
 * changes: names, enchantments and everything else on the item stay as they are.
 */
public final class WeaponLoreRefresher implements Listener {

    private final WeaponRegistry registry;
    private final Legendaries legendaries;

    public WeaponLoreRefresher(WeaponRegistry registry, Legendaries legendaries) {
        this.registry = registry;
        this.legendaries = legendaries;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerInventory inventory = event.getPlayer().getInventory();
        int size = inventory.getContents().length;
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (refresh(stack)) inventory.setItem(slot, stack);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Item item = event.getItem();
        ItemStack stack = item.getItemStack();
        if (refresh(stack)) item.setItemStack(stack);
    }

    /** Returns true if the stack's lore was changed. */
    private boolean refresh(ItemStack stack) {
        Legendaries.Weapon weapon = legendaries.identify(stack);
        if (weapon == null) return false;

        ItemStack fresh = registry.create(weapon.id());
        if (fresh == null) return false;

        List<Component> lore = fresh.lore();
        if (Objects.equals(stack.lore(), lore)) return false;
        stack.lore(lore);
        return true;
    }
}
