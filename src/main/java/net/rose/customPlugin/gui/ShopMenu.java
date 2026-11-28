package net.rose.customPlugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.rose.customPlugin.item.Legendaries;
import net.rose.customPlugin.item.WeaponRegistry;
import net.rose.customPlugin.item.misc.KillToken;
import net.rose.customPlugin.listener.player.WeaponLimitListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /shop: every weapon with its price in kill tokens. Clicking a weapon takes the tokens out of the player's
 * inventory and gives them the weapon. Prices come from {@code shop} in config.yml (a price per tier, with an
 * optional price for single weapons). Buying has nothing to do with the weapon limit: a weapon bought past the limit
 * is simply disabled until the player has room for it (see {@link WeaponLimitListener}).
 */
public final class ShopMenu implements Listener {

    private static final int WEAPONS_PER_ROW = 7;
    private static final int MAX_INNER_ROWS = 4;
    private static final int DEFAULT_PRICE = 5;

    private final JavaPlugin plugin;
    private final WeaponRegistry weapons;
    private final WeaponLimitListener limit;
    private final KillToken killToken;

    /** Marks an inventory as this menu and remembers which weapon sits in which slot. */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> weaponAt = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public ShopMenu(JavaPlugin plugin, WeaponRegistry weapons, WeaponLimitListener limit, KillToken killToken) {
        this.plugin = plugin;
        this.weapons = weapons;
        this.limit = limit;
        this.killToken = killToken;
    }

    /** What a weapon costs in kill tokens: its own price in config.yml if it has one, otherwise its tier's price. */
    public int priceOf(Legendaries.Weapon weapon) {
        int tierPrice = plugin.getConfig().getInt("shop.tier-prices." + limit.tierOf(weapon),
                plugin.getConfig().getInt("shop.default-price", DEFAULT_PRICE));
        return Math.max(1, plugin.getConfig().getInt("shop.weapon-prices." + weapon.id(), tierPrice));
    }

    public void open(Player player) {
        List<String> ids = new ArrayList<>();
        for (String id : weapons.ids()) {
            if (ids.size() < WEAPONS_PER_ROW * MAX_INNER_ROWS) ids.add(id);
        }
        int innerRows = Math.max(1, (ids.size() + WEAPONS_PER_ROW - 1) / WEAPONS_PER_ROW);

        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, (innerRows + 2) * 9,
                Component.text("Weapon Shop", NamedTextColor.DARK_GRAY));
        holder.inventory = inventory;
        for (int i = 0; i < ids.size(); i++) {
            holder.weaponAt.put((1 + i / WEAPONS_PER_ROW) * 9 + 1 + i % WEAPONS_PER_ROW, ids.get(i));
        }

        populate(holder, player);
        player.openInventory(inventory);
    }

    /** Fills (or refreshes) the menu: prices, what the player can afford, and their token balance. */
    private void populate(Holder holder, Player player) {
        Inventory inventory = holder.inventory;
        int tokens = killToken.count(player.getInventory());
        WeaponLimitListener.Summary summary = limit.summarize(player);
        boolean limited = limit.isLimited(player);

        ItemStack filler = filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        for (Map.Entry<Integer, String> entry : holder.weaponAt.entrySet()) {
            inventory.setItem(entry.getKey(), icon(entry.getValue(), tokens, summary, limited));
        }
        inventory.setItem(inventory.getSize() - 5, balanceItem(tokens, summary, limited));
    }

    // ---- clicks ---------------------------------------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true); // nothing can be taken out of, put into or moved around in the menu

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!holder.inventory.equals(event.getClickedInventory())) return;

        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;

        String id = holder.weaponAt.get(event.getSlot());
        if (id != null) buy(player, holder, id);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void buy(Player player, Holder holder, String id) {
        ItemStack weapon = weapons.create(id);
        Legendaries.Weapon info = limit.identify(weapon);
        if (weapon == null || info == null) return;

        int price = priceOf(info);
        PlayerInventory inventory = player.getInventory();
        int tokens = killToken.count(inventory);
        if (tokens < price) {
            player.sendActionBar(Component.text("Not enough kill tokens (" + tokens + "/" + price + ")", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Take the tokens first (they may free the slot the weapon needs), and give them back if it doesn't fit.
        ItemStack[] before = inventory.getStorageContents();
        for (int i = 0; i < before.length; i++) {
            if (before[i] != null) before[i] = before[i].clone();
        }
        killToken.take(inventory, price);
        if (!inventory.addItem(weapon).isEmpty()) {
            inventory.setStorageContents(before);
            player.sendActionBar(Component.text("Your inventory is full", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        player.sendMessage(Component.text("Bought ", NamedTextColor.GREEN)
                .append(Component.text(info.name(), info.color()))
                .append(Component.text(" for " + format(price), NamedTextColor.GREEN)));
        populate(holder, player);
    }

    // ---- items ----------------------------------------------------------------------------------------------------

    private ItemStack icon(String id, int tokens, WeaponLimitListener.Summary summary, boolean limited) {
        ItemStack item = weapons.create(id);
        Legendaries.Weapon weapon = limit.identify(item);

        List<Component> lore = new ArrayList<>();
        if (item.lore() != null) lore.addAll(item.lore());
        lore.add(Component.empty());

        if (weapon != null) {
            int tier = limit.tierOf(weapon);
            int points = limit.pointsOf(weapon);
            int price = priceOf(weapon);
            boolean affordable = tokens >= price;

            lore.add(line("Tier " + tier, NamedTextColor.GOLD)
                    .append(Component.text(" - " + (points == 1 ? "1 weapon point" : points + " weapon points"), NamedTextColor.GRAY)));
            lore.add(line("Price: ", NamedTextColor.GRAY)
                    .append(Component.text(format(price), affordable ? NamedTextColor.GREEN : NamedTextColor.RED)));
            if (limited && summary.used() + points > summary.max()) {
                lore.add(line("Over your weapon limit: it would be disabled", NamedTextColor.RED));
            }
            lore.add(line(affordable ? "Click to buy" : "Not enough kill tokens",
                    affordable ? NamedTextColor.YELLOW : NamedTextColor.DARK_RED));
        }
        item.lore(lore);
        return item;
    }

    private ItemStack balanceItem(int tokens, WeaponLimitListener.Summary summary, boolean limited) {
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(line("Your kill tokens: ", NamedTextColor.RED)
                .append(Component.text(String.valueOf(tokens), NamedTextColor.WHITE)));

        List<Component> lore = new ArrayList<>();
        lore.add(line("Players drop a kill token when they die", NamedTextColor.GRAY));
        if (limited) {
            lore.add(line("Weapon points: " + summary.used() + "/" + summary.max() + " (see /weaponpoints)", NamedTextColor.GRAY));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
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
        return amount + (amount == 1 ? " kill token" : " kill tokens");
    }
}
