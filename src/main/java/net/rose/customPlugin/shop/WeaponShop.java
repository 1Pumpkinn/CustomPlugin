package net.rose.customPlugin.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.rose.customPlugin.item.WeaponRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chest GUI where players spend points on weapons. Prices come from {@code shop.prices.<weapon>} in config.yml
 * (a negative price takes the weapon off the shop).
 */
public final class WeaponShop implements Listener {

    private static final int DEFAULT_PRICE = 10;
    private static final int WEAPONS_PER_ROW = 7;
    private static final int MAX_INNER_ROWS = 4;

    private final JavaPlugin plugin;
    private final WeaponRegistry weapons;
    private final Points points;

    /** Marks an inventory as the shop and remembers which weapon sits in which slot. */
    private static final class Holder implements InventoryHolder {
        private final Map<Integer, String> slots = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public WeaponShop(JavaPlugin plugin, WeaponRegistry weapons, Points points) {
        this.plugin = plugin;
        this.weapons = weapons;
        this.points = points;
    }

    public void open(Player player) {
        List<String> sold = new ArrayList<>();
        for (String id : weapons.ids()) {
            if (price(id) >= 0 && sold.size() < WEAPONS_PER_ROW * MAX_INNER_ROWS) sold.add(id);
        }

        int innerRows = (sold.size() + WEAPONS_PER_ROW - 1) / WEAPONS_PER_ROW;
        int rows = innerRows + 2;

        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                Component.text("Weapon Shop", NamedTextColor.DARK_GRAY));
        holder.inventory = inventory;

        ItemStack filler = filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        for (int i = 0; i < sold.size(); i++) {
            int slot = (1 + i / WEAPONS_PER_ROW) * 9 + 1 + i % WEAPONS_PER_ROW;
            inventory.setItem(slot, icon(sold.get(i)));
            holder.slots.put(slot, sold.get(i));
        }
        inventory.setItem(balanceSlot(inventory), balanceItem(player.getUniqueId()));

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!top.equals(event.getClickedInventory())) return;
        if (!event.getClick().isLeftClick() && !event.getClick().isRightClick()) return;

        String id = holder.slots.get(event.getSlot());
        if (id != null) buy(player, holder, id);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void buy(Player player, Holder holder, String id) {
        int price = price(id);
        if (price < 0) return;

        UUID uuid = player.getUniqueId();
        if (!points.spend(uuid, price)) {
            player.sendMessage(Component.text("You need " + format(price) + " for that, you have "
                    + format(points.get(uuid)) + ".", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        ItemStack weapon = weapons.create(id);
        // Full inventory -> the weapon drops at the player's feet
        for (ItemStack leftover : player.getInventory().addItem(weapon).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        player.sendMessage(Component.text("Bought ", NamedTextColor.GREEN)
                .append(weapon.effectiveName())
                .append(Component.text(" for " + format(price) + ".", NamedTextColor.GREEN)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        holder.inventory.setItem(balanceSlot(holder.inventory), balanceItem(uuid));
    }

    private int price(String id) {
        return plugin.getConfig().getInt("shop.prices." + id, DEFAULT_PRICE);
    }

    private static int balanceSlot(Inventory inventory) {
        return inventory.getSize() - 5;
    }

    private ItemStack icon(String id) {
        ItemStack item = weapons.create(id);
        List<Component> lore = new ArrayList<>();
        if (item.lore() != null) lore.addAll(item.lore());
        lore.add(Component.empty());
        lore.add(Component.text("Price: ", NamedTextColor.GRAY)
                .append(Component.text(format(price(id)), NamedTextColor.GOLD))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Click to buy", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        item.lore(lore);
        return item;
    }

    private ItemStack balanceItem(UUID player) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Your points: ", NamedTextColor.GOLD)
                .append(Component.text(points.get(player), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Earn points by killing players", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
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
