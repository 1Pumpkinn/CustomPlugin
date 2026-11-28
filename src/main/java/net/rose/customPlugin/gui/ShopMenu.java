package net.rose.customPlugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.rose.customPlugin.item.Legendaries;
import net.rose.customPlugin.item.WeaponRegistry;
import net.rose.customPlugin.item.misc.KillToken;
import net.rose.customPlugin.item.WeaponLimit;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * /shop: weapons for sale in kill tokens, one page per tier (the tabs along the bottom row switch pages).
 * Each page lists its weapons from the lowest price to the highest. Clicking a weapon takes the tokens out of the player's inventory and gives them the weapon.
 *
 * <p>Prices come from config.yml (see WeaponSettings). A weapon's price is, in order: {@code weapons.<id>.price},
 * then its tier's {@code shop.tier-prices.<tier>}, then {@code shop.default-price}. Buying has nothing to do with the weapon limit:
 * a weapon bought past the limit is simply disabled until the player has room for it
 * (see {@link WeaponLimit}).
 */
public final class ShopMenu implements Listener {

    private static final int ROWS = 5;                 // three rows of weapons and a bottom row for tabs and balance
    private static final int WEAPONS_PER_ROW = 7;
    private static final int WEAPON_ROWS = ROWS - 2;
    private static final int MAX_TABS = 6;

    private static final Material[] TAB_ICONS = {
            Material.NETHER_STAR, Material.DIAMOND, Material.GOLD_INGOT,
            Material.IRON_INGOT, Material.COPPER_INGOT, Material.COAL};

    private final JavaPlugin plugin;
    private final WeaponRegistry weapons;
    private final WeaponLimit limit;
    private final KillToken killToken;

    /** Marks an inventory as this menu and remembers its state: the tier on show and what sits in which slot. */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final Map<String, Integer> tierOf = new LinkedHashMap<>(); // weapon id -> tier, in registry order
        private final List<Integer> tiers = new ArrayList<>();             // tiers that have weapons, lowest first
        private int tier;
        private final Map<Integer, String> weaponAt = new HashMap<>();
        private final Map<Integer, Integer> tabAt = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public ShopMenu(JavaPlugin plugin, WeaponRegistry weapons, WeaponLimit limit, KillToken killToken) {
        this.plugin = plugin;
        this.weapons = weapons;
        this.limit = limit;
        this.killToken = killToken;
    }

    /** What a weapon costs in kill tokens (see the class comment for where the price comes from). */
    public int priceOf(Legendaries.Weapon weapon) {
        return limit.settings().price(weapon.id());
    }

    public void open(Player player) {
        Holder holder = new Holder();
        for (String id : weapons.ids()) {
            Legendaries.Weapon weapon = limit.identify(weapons.create(id));
            if (weapon != null) holder.tierOf.put(id, limit.tierOf(weapon));
        }
        for (int tier : new TreeSet<>(holder.tierOf.values())) {
            if (holder.tiers.size() < MAX_TABS) holder.tiers.add(tier);
        }
        if (holder.tiers.isEmpty()) return;
        holder.tier = holder.tiers.get(0);

        Inventory inventory = Bukkit.createInventory(holder, ROWS * 9, Component.text("Weapon Shop", NamedTextColor.DARK_GRAY));
        holder.inventory = inventory;

        populate(holder, player);
        player.openInventory(inventory);
    }

    /** Fills (or refreshes) the menu for the tier on show: its weapons, the tabs and the player's token balance. */
    private void populate(Holder holder, Player player) {
        Inventory inventory = holder.inventory;
        int tokens = killToken.count(player.getInventory());
        WeaponLimit.Summary summary = limit.summarize(player);
        boolean limited = limit.isLimited(player);

        ItemStack filler = filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        // This tier's weapons, cheapest first (weapons with the same price stay in registry order).
        Map<String, Integer> prices = new HashMap<>();
        for (Map.Entry<String, Integer> entry : holder.tierOf.entrySet()) {
            if (entry.getValue() != holder.tier) continue;
            Legendaries.Weapon weapon = limit.identify(weapons.create(entry.getKey()));
            if (weapon != null) prices.put(entry.getKey(), priceOf(weapon));
        }
        List<String> page = new ArrayList<>();
        for (String id : holder.tierOf.keySet()) {
            if (prices.containsKey(id)) page.add(id);
        }
        page.sort(Comparator.comparingInt(prices::get));

        holder.weaponAt.clear();
        for (int i = 0; i < page.size() && i < WEAPONS_PER_ROW * WEAPON_ROWS; i++) {
            int slot = (1 + i / WEAPONS_PER_ROW) * 9 + 1 + i % WEAPONS_PER_ROW;
            holder.weaponAt.put(slot, page.get(i));
            inventory.setItem(slot, icon(page.get(i), tokens, summary, limited, limit.blocksDuplicates(player)));
        }

        int bottom = inventory.getSize() - 9;
        holder.tabAt.clear();
        for (int i = 0; i < holder.tiers.size(); i++) {
            holder.tabAt.put(bottom + 1 + i, holder.tiers.get(i));
            inventory.setItem(bottom + 1 + i, tab(holder, holder.tiers.get(i), i));
        }
        inventory.setItem(bottom + 8, balanceItem(tokens, summary, limited));
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

        Integer tab = holder.tabAt.get(event.getSlot());
        if (tab != null) {
            if (tab != holder.tier) {
                holder.tier = tab;
                populate(holder, player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            }
            return;
        }

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

        if (limit.blocksDuplicates(player) && owns(limit.summarize(player), id)) {
            player.sendActionBar(Component.text("You already have " + info.name(), NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

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

    /** Whether the player already carries this weapon. */
    private static boolean owns(WeaponLimit.Summary summary, String id) {
        return summary.weapons().stream().anyMatch(carried -> carried.weapon().id().equals(id));
    }

    // ---- items ----------------------------------------------------------------------------------------------------

    private ItemStack icon(String id, int tokens, WeaponLimit.Summary summary, boolean limited, boolean noDuplicates) {
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
            if (noDuplicates && owns(summary, id)) {
                lore.add(line("You already have this weapon", NamedTextColor.RED));
            } else {
                if (limited && summary.used() + points > summary.max()) {
                    lore.add(line("Over your weapon limit: it would be disabled", NamedTextColor.RED));
                }
                lore.add(line(affordable ? "Click to buy" : "Not enough kill tokens",
                        affordable ? NamedTextColor.YELLOW : NamedTextColor.DARK_RED));
            }
        }
        item.lore(lore);
        return item;
    }

    /** One tab per tier along the bottom row; the tier on show glows. */
    private ItemStack tab(Holder holder, int tier, int index) {
        boolean selected = tier == holder.tier;
        int count = (int) holder.tierOf.values().stream().filter(t -> t == tier).count();
        int points = limit.pointsForTier(tier);

        ItemStack item = new ItemStack(TAB_ICONS[index % TAB_ICONS.length]);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(line("Tier " + tier, selected ? NamedTextColor.YELLOW : NamedTextColor.GOLD));
        meta.lore(List.of(
                line(count + (count == 1 ? " weapon" : " weapons"), NamedTextColor.GRAY),
                line("Costs " + (points == 1 ? "1 weapon point" : points + " weapon points") + " by default", NamedTextColor.GRAY),
                line(selected ? "Viewing" : "Click to view", selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
        meta.setEnchantmentGlintOverride(selected);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack balanceItem(int tokens, WeaponLimit.Summary summary, boolean limited) {
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(line("Your kill tokens: ", NamedTextColor.RED)
                .append(Component.text(String.valueOf(tokens), NamedTextColor.WHITE)));

        List<Component> lore = new ArrayList<>();
        lore.add(line("Players drop a kill token when they die", NamedTextColor.GRAY));
        if (limited) {
            lore.add(line("Weapon points: " + summary.total() + "/" + summary.max() + " (see /weaponpoints)",
                    limited && summary.total() > summary.max() ? NamedTextColor.RED : NamedTextColor.GRAY));
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