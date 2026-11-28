package net.rose.customPlugin.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The weapon limit's rules. Every custom weapon in the inventory costs weapon points by tier ({@code weapon-limit}
 * in config.yml, shown to players by /weaponpoints). Weapons are counted from least to most expensive (equally
 * expensive ones in the order they were picked up, see {@link PickupOrder}), and a weapon that would take the total
 * over {@code weapon-limit.max-points} is disabled. So when a player carries too much, it is the most expensive
 * weapons that stop working, and the cheaper ones keep working.
 *
 * <p>This class only answers questions. Blocking disabled weapons is {@code WeaponLimitListener}, and telling
 * players about it is {@code WeaponLimitNotifier}.
 */
public final class WeaponLimit {

    public static final String BYPASS_PERMISSION = "customplugin.weaponlimit.bypass";

    private static final int OFFHAND_SLOT = 40;
    private static final int STORAGE_SLOTS = 36;

    private final JavaPlugin plugin;
    private final Legendaries legendaries;
    private final WeaponSettings settings;
    private final PickupOrder pickupOrder;

    public WeaponLimit(JavaPlugin plugin, Legendaries legendaries) {
        this.plugin = plugin;
        this.legendaries = legendaries;
        this.settings = new WeaponSettings(plugin);
        this.pickupOrder = new PickupOrder(plugin);
    }

    /** One weapon a player carries: what it costs and whether the limit has disabled it. */
    public record Carried(Legendaries.Weapon weapon, int slot, int cost, boolean disabled) {
    }

    /**
     * What a player carries: points used by the active weapons, the limit, the points of everything carried (disabled
     * weapons included, so it can be over the limit), and every weapon from least to most expensive.
     */
    public record Summary(int used, int max, int total, List<Carried> weapons) {
    }

    // ---- config -----------------------------------------------------------------------------------------------------

    /** Tier, points and price of every weapon, straight from config.yml ({@code weapons.<id>}). */
    public WeaponSettings settings() {
        return settings;
    }

    /** The tier a weapon is in ({@code weapons.<id>.tier}); weapons not listed use {@code weapon-limit.default-tier}. */
    public int tierOf(Legendaries.Weapon weapon) {
        return settings.tier(weapon.id());
    }

    /** What a tier costs ({@code weapon-limit.tier-points.<tier>}). */
    public int pointsForTier(int tier) {
        return settings.pointsForTier(tier);
    }

    /** What one copy of the weapon costs in weapon points. */
    public int pointsOf(Legendaries.Weapon weapon) {
        return settings.points(weapon.id());
    }

    /** Every tier from config.yml with its point cost, lowest tier first. */
    public Map<Integer, Integer> tiers() {
        Map<Integer, Integer> tiers = new TreeMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("weapon-limit.tier-points");
        if (section == null) return tiers;
        for (String key : section.getKeys(false)) {
            try {
                tiers.put(Integer.parseInt(key), Math.max(0, section.getInt(key)));
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("Ignoring invalid tier in weapon-limit.tier-points: " + key);
            }
        }
        return tiers;
    }

    public int maxPoints() {
        return Math.max(0, plugin.getConfig().getInt("weapon-limit.max-points", 6));
    }

    /** Whether the limit is switched on at all ({@code weapon-limit.enabled}), read each time so a reload applies it. */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("weapon-limit.enabled", true);
    }

    /** Whether the weapon limit applies to this player at all. */
    public boolean isLimited(Player player) {
        return isEnabled() && !player.hasPermission(BYPASS_PERMISSION);
    }

    /** Which custom weapon a stack is, or null. */
    public Legendaries.Weapon identify(ItemStack stack) {
        return legendaries.identify(stack);
    }

    // ---- what a player carries ----------------------------------------------------------------------------------------

    public Summary summarize(Player player) {
        boolean limited = isLimited(player);
        int maxPoints = maxPoints();

        // Every weapon in slot order: hotbar, the rest of the inventory, then the offhand.
        PlayerInventory inventory = player.getInventory();
        List<Carried> slotOrder = new ArrayList<>();
        for (int slot = 0; slot <= OFFHAND_SLOT; slot++) {
            if (slot >= STORAGE_SLOTS && slot < OFFHAND_SLOT) continue; // armor slots hold no weapons
            ItemStack stack = inventory.getItem(slot);
            Legendaries.Weapon weapon = legendaries.identify(stack);
            if (weapon == null) continue;
            slotOrder.add(new Carried(weapon, slot, pointsOf(weapon) * Math.max(1, stack.getAmount()), false));
        }

        // Points are handed out cheapest weapon first, so the ones that take the total over the limit are the most
        // expensive. Equally expensive weapons count in the order they were picked up.
        int[] age = pickupOrder.ages(player.getUniqueId(), slotOrder.stream().map(c -> c.weapon().id()).toList());
        List<Integer> counted = new ArrayList<>();
        for (int i = 0; i < slotOrder.size(); i++) counted.add(i);
        counted.sort(Comparator.comparingInt((Integer i) -> slotOrder.get(i).cost()).thenComparingInt(i -> age[i]));

        Set<Integer> disabledSlots = new HashSet<>();
        int used = 0;
        for (int i : counted) {
            Carried carried = slotOrder.get(i);
            if (limited && used + carried.cost() > maxPoints) {
                disabledSlots.add(carried.slot());
            } else {
                used += carried.cost();
            }
        }

        List<Carried> result = new ArrayList<>();
        for (int i : counted) {
            Carried carried = slotOrder.get(i);
            result.add(new Carried(carried.weapon(), carried.slot(), carried.cost(), disabledSlots.contains(carried.slot())));
        }
        int total = 0;
        for (Carried carried : slotOrder) total += carried.cost();
        return new Summary(used, maxPoints, total, result);
    }

    /** Inventory slot -> weapon, for every weapon the player carries that is over the limit. */
    public Map<Integer, Legendaries.Weapon> disabledSlots(Player player) {
        Map<Integer, Legendaries.Weapon> disabled = new LinkedHashMap<>();
        if (!isEnabled()) return disabled;
        for (Carried carried : summarize(player).weapons()) {
            if (carried.disabled()) disabled.put(carried.slot(), carried.weapon());
        }
        return disabled;
    }

    /** The weapon in that hand if it is disabled, otherwise null. */
    public Legendaries.Weapon disabledIn(Player player, EquipmentSlot hand) {
        if (!isEnabled()) return null;
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return null;
        PlayerInventory inventory = player.getInventory();
        int slot = hand == EquipmentSlot.OFF_HAND ? OFFHAND_SLOT : inventory.getHeldItemSlot();
        // Cheap exit for the common case of a hand holding something that is not a weapon.
        if (legendaries.identify(inventory.getItem(slot)) == null) return null;
        return disabledSlots(player).get(slot);
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------------------

    /** The player left: their pickup order is remembered for a while, then dropped. */
    public void onQuit(UUID player) {
        pickupOrder.forgetLater(player);
    }

    public void shutdown() {
        pickupOrder.clear();
    }
}
