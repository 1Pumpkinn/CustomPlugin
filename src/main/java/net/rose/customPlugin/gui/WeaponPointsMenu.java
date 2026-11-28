package net.rose.customPlugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.rose.customPlugin.item.Legendaries;
import net.rose.customPlugin.item.WeaponRegistry;
import net.rose.customPlugin.listener.player.WeaponLimitListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /weaponpoints: a chest GUI with every weapon, its tier and what it costs, plus how many weapon points the player is
 * using. It is display only: nothing in it can be taken, bought or moved.
 */
public final class WeaponPointsMenu implements Listener {

    private static final int WEAPONS_PER_ROW = 7;
    private static final int MAX_INNER_ROWS = 4;

    private final WeaponRegistry weapons;
    private final WeaponLimitListener limit;

    /** Marks an inventory as this menu. */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public WeaponPointsMenu(WeaponRegistry weapons, WeaponLimitListener limit) {
        this.weapons = weapons;
        this.limit = limit;
    }

    public void open(Player player) {
        WeaponLimitListener.Summary summary = limit.summarize(player);
        Map<String, WeaponLimitListener.Carried> carried = new HashMap<>();
        for (WeaponLimitListener.Carried entry : summary.weapons()) {
            carried.merge(entry.weapon().id(), entry, (a, b) -> a.disabled() ? a : b);
        }

        List<String> ids = new ArrayList<>();
        for (String id : weapons.ids()) {
            if (ids.size() < WEAPONS_PER_ROW * MAX_INNER_ROWS) ids.add(id);
        }

        int innerRows = Math.max(1, (ids.size() + WEAPONS_PER_ROW - 1) / WEAPONS_PER_ROW);
        int rows = innerRows + 2;

        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                Component.text("Weapon Points", NamedTextColor.DARK_GRAY));
        holder.inventory = inventory;

        ItemStack filler = filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        for (int i = 0; i < ids.size(); i++) {
            int slot = (1 + i / WEAPONS_PER_ROW) * 9 + 1 + i % WEAPONS_PER_ROW;
            inventory.setItem(slot, icon(ids.get(i), carried.get(ids.get(i))));
        }
        inventory.setItem(inventory.getSize() - 5, balanceItem(summary));

        player.openInventory(inventory);
    }

    /** Nothing can be taken out of, put into or moved around in the menu. */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private ItemStack icon(String id, WeaponLimitListener.Carried carried) {
        ItemStack item = weapons.create(id);
        Legendaries.Weapon weapon = limit.identify(item);
        int tier = weapon == null ? -1 : limit.tierOf(weapon);
        int cost = tier < 0 ? -1 : limit.pointsForTier(tier);

        List<Component> lore = new ArrayList<>();
        if (item.lore() != null) lore.addAll(item.lore());
        lore.add(Component.empty());
        if (tier > 0) {
            lore.add(Component.text("Tier " + tier, NamedTextColor.GOLD)
                    .append(Component.text(" - " + format(cost), NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (carried != null) {
            lore.add(carried.disabled()
                    ? Component.text("Disabled: over your weapon limit", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false)
                    : Component.text("In your inventory", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
        }
        item.lore(lore);
        return item;
    }

    private ItemStack balanceItem(WeaponLimitListener.Summary summary) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Your weapon points: ", NamedTextColor.GOLD)
                .append(Component.text(summary.used() + "/" + summary.max(), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        for (Map.Entry<Integer, Integer> tier : limit.tiers().entrySet()) {
            lore.add(Component.text("Tier " + tier.getKey() + " = " + format(tier.getValue()), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("A weapon that goes over " + summary.max() + " is disabled", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private static String format(int amount) {
        return amount + (amount == 1 ? " point" : " points");
    }
}
