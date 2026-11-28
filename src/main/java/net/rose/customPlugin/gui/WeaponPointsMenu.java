package net.rose.customPlugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /weaponpoints: the weapons the player is carrying, what each one costs, which of them the weapon limit has
 * disabled, and how many points they are using. Weapons are bought in /shop ({@link ShopMenu}); the button in the
 * corner opens it. Nothing in this menu can be taken or moved.
 */
public final class WeaponPointsMenu implements Listener {

    private static final int WEAPONS_PER_ROW = 7;
    private static final int MAX_INNER_ROWS = 4;

    private final JavaPlugin plugin;
    private final WeaponLimitListener limit;
    private final ShopMenu shop;

    /** Marks an inventory as this menu and remembers where its shop button is. */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private int shopSlot;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public WeaponPointsMenu(JavaPlugin plugin, WeaponLimitListener limit, ShopMenu shop) {
        this.plugin = plugin;
        this.limit = limit;
        this.shop = shop;
    }

    public void open(Player player) {
        WeaponLimitListener.Summary summary = limit.summarize(player);
        List<WeaponLimitListener.Carried> carried = summary.weapons();
        int shown = Math.min(carried.size(), WEAPONS_PER_ROW * MAX_INNER_ROWS);

        int innerRows = Math.max(1, (shown + WEAPONS_PER_ROW - 1) / WEAPONS_PER_ROW);
        int rows = innerRows + 2;

        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                Component.text("Weapon Points", NamedTextColor.DARK_GRAY));
        holder.inventory = inventory;

        ItemStack filler = filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        if (shown == 0) {
            inventory.setItem(9 + 4, emptyItem());
        }
        for (int i = 0; i < shown; i++) {
            int slot = (1 + i / WEAPONS_PER_ROW) * 9 + 1 + i % WEAPONS_PER_ROW;
            inventory.setItem(slot, icon(player, carried.get(i)));
        }

        int bottom = inventory.getSize() - 9;
        inventory.setItem(bottom + 4, balanceItem(player, summary));
        holder.shopSlot = bottom + 6;
        inventory.setItem(holder.shopSlot, shopButton());

        player.openInventory(inventory);
    }

    /** Nothing can be taken out of, put into or moved around in the menu; the shop button opens the shop. */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!holder.inventory.equals(event.getClickedInventory()) || event.getSlot() != holder.shopSlot) return;

        // Opening a menu from inside a click on another one is safest a tick later.
        plugin.getServer().getScheduler().runTask(plugin, () -> shop.open(player));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    // ---- items ----------------------------------------------------------------------------------------------------

    /** The weapon as the player has it, with what it costs and whether it is active. */
    private ItemStack icon(Player player, WeaponLimitListener.Carried carried) {
        ItemStack item = player.getInventory().getItem(carried.slot());
        item = item == null ? new ItemStack(Material.BARRIER) : item.clone();

        List<Component> lore = new ArrayList<>();
        if (item.lore() != null) lore.addAll(item.lore());
        lore.add(Component.empty());
        lore.add(line("Tier " + limit.tierOf(carried.weapon()), NamedTextColor.GOLD)
                .append(Component.text(" - " + format(carried.cost()), NamedTextColor.GRAY)));
        lore.add(carried.disabled()
                ? line("Disabled: over your weapon limit", NamedTextColor.RED)
                : line("Active", NamedTextColor.GREEN));
        item.lore(lore);
        if (carried.disabled()) {
            // Make a disabled weapon stand out at a glance: red "DISABLED" in front of its name.
            Component name = item.effectiveName().decoration(TextDecoration.BOLD, false);
            item.editMeta(meta -> meta.displayName(line("DISABLED ", NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true)
                    .append(name)));
        }
        return item;
    }

    private ItemStack balanceItem(Player player, WeaponLimitListener.Summary summary) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        boolean limited = limit.isLimited(player);
        int over = summary.total() - summary.max();
        boolean isOver = limited && over > 0;
        // Shows everything carried, so going over the limit reads as e.g. 7/6 (in red) instead of hiding the extra points.
        meta.displayName(line("Your weapon points: ", NamedTextColor.GOLD)
                .append(Component.text(summary.total() + "/" + summary.max(), isOver ? NamedTextColor.RED : NamedTextColor.WHITE)));

        List<Component> lore = new ArrayList<>();
        if (isOver) {
            lore.add(line("Over the limit by " + format(over), NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true));
            lore.add(line(summary.used() + "/" + summary.max() + " points are active, the rest is disabled", NamedTextColor.RED));
            lore.add(Component.empty());
        }
        for (Map.Entry<Integer, Integer> tier : limit.tiers().entrySet()) {
            lore.add(line("Tier " + tier.getKey() + " = " + format(tier.getValue()), NamedTextColor.GRAY));
        }
        if (limited) {
            lore.add(line("The weapon that takes you over " + summary.max() + " is disabled", NamedTextColor.GRAY));
        } else {
            lore.add(line("You are not affected by the weapon limit", NamedTextColor.GRAY));
        }
        meta.lore(lore);
        if (isOver) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack shopButton() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(line("Weapon Shop", NamedTextColor.GREEN));
        meta.lore(List.of(line("Buy weapons with kill tokens", NamedTextColor.GRAY),
                line("Click to open (or use /shop)", NamedTextColor.YELLOW)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack emptyItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(line("You aren't carrying any weapons", NamedTextColor.RED));
        meta.lore(List.of(line("Buy some in the weapon shop (/shop)", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
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
