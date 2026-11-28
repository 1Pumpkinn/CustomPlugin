package net.rose.customPlugin.listener.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.item.Legendaries;
import net.rose.customPlugin.item.WeaponSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Caps how many weapons a player can use at once. Every custom weapon in the inventory costs weapon points by tier
 * ({@code weapon-limit} in config.yml, shown to players by /weaponpoints). Weapons are counted oldest first (the
 * order the player picked them up in), and a weapon that would take the total over
 * {@code weapon-limit.max-points} is disabled: it can't be used for its abilities or to attack. Only the weapon that
 * goes over is disabled; the ones the player already had keep working.
 *
 * <p>Everything here runs at LOWEST priority, so the weapon listeners (which skip cancelled events) never see a
 * disabled weapon being used.
 */
public final class WeaponLimitListener implements Listener {

    public static final String BYPASS_PERMISSION = "customplugin.weaponlimit.bypass";

    private static final int OFFHAND_SLOT = 40;
    private static final int STORAGE_SLOTS = 36;
    private static final long CHECK_INTERVAL_TICKS = 20L;
    /** How long a player's pickup order is remembered after they log out (30 minutes). */
    private static final long FORGET_PICKUP_ORDER_TICKS = 30L * 60L * 20L;

    private final JavaPlugin plugin;
    private final Legendaries legendaries;
    private final WeaponSettings settings;

    /** The weapons (by id) each online player was last told are disabled, so a message only goes out on a change. */
    private final Map<UUID, List<String>> notified = new HashMap<>();
    /**
     * The weapon ids each player carries, oldest pickup first (a weapon carried twice appears twice). A newly
     * picked up weapon goes on the end, so it is the one that goes over the limit. Kept in memory only: after a
     * restart the order starts out as slot order.
     */
    private final Map<UUID, List<String>> acquired = new HashMap<>();
    private final BukkitTask checkTask;

    public WeaponLimitListener(JavaPlugin plugin, Legendaries legendaries) {
        this.plugin = plugin;
        this.legendaries = legendaries;
        this.settings = new WeaponSettings(plugin);
        // Always scheduled: enabled / max-points are read from the config each time, so /customplugin reload applies them.
        this.checkTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkAll, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    public void shutdown() {
        checkTask.cancel();
        notified.clear();
        acquired.clear();
    }

    // ---- limit logic ----------------------------------------------------------------------------------------------

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

    /** One weapon a player carries: what it costs and whether the limit has disabled it. */
    public record Carried(Legendaries.Weapon weapon, int slot, int cost, boolean disabled) {
    }

    /**
     * What a player carries: points used by the active weapons, the limit, the points of everything carried (disabled
     * weapons included, so it can be over the limit), and every weapon in slot order.
     */
    public record Summary(int used, int max, int total, List<Carried> weapons) {
    }

    /** Which custom weapon a stack is, or null. */
    public Legendaries.Weapon identify(ItemStack stack) {
        return legendaries.identify(stack);
    }

    public int maxPoints() {
        return Math.max(0, plugin.getConfig().getInt("weapon-limit.max-points", 6));
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("weapon-limit.enabled", true);
    }

    /** Whether the weapon limit applies to this player at all. */
    public boolean isLimited(Player player) {
        return enabled() && !player.hasPermission(BYPASS_PERMISSION);
    }

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

        // Points are handed out oldest weapon first, so the one that takes the total over the limit is the newest.
        Map<Carried, Integer> age = pickupOrder(player.getUniqueId(), slotOrder);
        List<Carried> byAge = new ArrayList<>(slotOrder);
        byAge.sort(Comparator.comparingInt(age::get));

        Set<Integer> disabledSlots = new HashSet<>();
        int used = 0;
        for (Carried carried : byAge) {
            if (limited && used + carried.cost() > maxPoints) {
                disabledSlots.add(carried.slot());
            } else {
                used += carried.cost();
            }
        }

        List<Carried> result = new ArrayList<>();
        for (Carried carried : slotOrder) {
            result.add(new Carried(carried.weapon(), carried.slot(), carried.cost(), disabledSlots.contains(carried.slot())));
        }
        int total = 0;
        for (Carried carried : slotOrder) total += carried.cost();
        return new Summary(used, maxPoints, total, result);
    }

    /**
     * Updates the player's pickup order (weapons they no longer carry drop out, new ones go on the end, in slot
     * order if several showed up at once) and returns each carried weapon's place in it.
     */
    private Map<Carried, Integer> pickupOrder(UUID id, List<Carried> slotOrder) {
        List<String> order = acquired.computeIfAbsent(id, k -> new ArrayList<>());

        Map<String, Integer> carriedCount = new HashMap<>();
        for (Carried carried : slotOrder) carriedCount.merge(carried.weapon().id(), 1, Integer::sum);

        // Forget what is no longer carried.
        Map<String, Integer> kept = new HashMap<>();
        for (Iterator<String> it = order.iterator(); it.hasNext(); ) {
            String weaponId = it.next();
            int have = kept.getOrDefault(weaponId, 0);
            if (have < carriedCount.getOrDefault(weaponId, 0)) {
                kept.put(weaponId, have + 1);
            } else {
                it.remove();
            }
        }

        // Whatever is carried beyond what was already known is new.
        Map<String, Integer> seen = new HashMap<>();
        for (Carried carried : slotOrder) {
            String weaponId = carried.weapon().id();
            if (seen.merge(weaponId, 1, Integer::sum) > kept.getOrDefault(weaponId, 0)) order.add(weaponId);
        }

        // The n-th copy of a weapon takes the n-th place that weapon holds in the order.
        Map<String, Integer> nextCopy = new HashMap<>();
        Map<Carried, Integer> age = new IdentityHashMap<>();
        for (Carried carried : slotOrder) {
            int copy = nextCopy.merge(carried.weapon().id(), 1, Integer::sum);
            int found = 0;
            for (int i = 0; i < order.size(); i++) {
                if (order.get(i).equals(carried.weapon().id()) && ++found == copy) {
                    age.put(carried, i);
                    break;
                }
            }
        }
        if (order.isEmpty()) acquired.remove(id);
        return age;
    }

    /** Inventory slot -> weapon, for every weapon the player carries that is over the limit. */
    private Map<Integer, Legendaries.Weapon> disabledSlots(Player player) {
        Map<Integer, Legendaries.Weapon> disabled = new LinkedHashMap<>();
        if (!enabled()) return disabled;
        for (Carried carried : summarize(player).weapons()) {
            if (carried.disabled()) disabled.put(carried.slot(), carried.weapon());
        }
        return disabled;
    }

    /** The weapon in that hand if it is disabled, otherwise null. */
    private Legendaries.Weapon disabledIn(Player player, EquipmentSlot hand) {
        if (!enabled()) return null;
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return null;
        PlayerInventory inventory = player.getInventory();
        int slot = hand == EquipmentSlot.OFF_HAND ? OFFHAND_SLOT : inventory.getHeldItemSlot();
        // Cheap exit for the common case of a hand holding something that is not a weapon.
        if (legendaries.identify(inventory.getItem(slot)) == null) return null;
        return disabledSlots(player).get(slot);
    }

    private void tell(Player player, Legendaries.Weapon weapon) {
        int maxPoints = maxPoints();
        player.sendActionBar(Component.text(weapon.name() + " is disabled: weapon limit reached ("
                + maxPoints + (maxPoints == 1 ? " point)" : " points)"), NamedTextColor.RED));
    }

    // ---- blocking ---------------------------------------------------------------------------------------------------

    /**
     * Only the item use is denied (blocks like chests still work). Listeners that don't use ignoreCancelled already
     * check {@code useItemInHand() == DENY}.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getHand() == null) return;
        Legendaries.Weapon weapon = disabledIn(event.getPlayer(), event.getHand());
        if (weapon == null) return;

        event.setUseItemInHand(Event.Result.DENY);
        if (event.getAction().isRightClick()) tell(event.getPlayer(), weapon);
    }

    /** The offhand key is how most weapons are primed, so a disabled weapon in the main hand can't be swapped. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Legendaries.Weapon weapon = disabledIn(event.getPlayer(), EquipmentSlot.HAND);
        if (weapon == null) return;

        event.setCancelled(true);
        tell(event.getPlayer(), weapon);
    }

    /** A disabled weapon doesn't hurt anyone, so its hit effects and passives never trigger either. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        Legendaries.Weapon weapon = disabledIn(player, EquipmentSlot.HAND);
        if (weapon == null) return;

        event.setCancelled(true);
        tell(player, weapon);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Legendaries.Weapon weapon = disabledIn(player, event.getHand());
        if (weapon == null) return;

        event.setCancelled(true);
        tell(player, weapon);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident thrown)) return;
        if (!(thrown.getShooter() instanceof Player player)) return;

        EquipmentSlot hand = player.getInventory().getItemInMainHand().isSimilar(thrown.getItemStack())
                ? EquipmentSlot.HAND
                : EquipmentSlot.OFF_HAND;
        Legendaries.Weapon weapon = disabledIn(player, hand);
        if (weapon == null) return;

        event.setCancelled(true);
        tell(player, weapon);
    }

    // ---- notifications ----------------------------------------------------------------------------------------------

    /** Once a second: tell players when the set of weapons that are disabled for them changes. */
    private void checkAll() {
        if (!enabled()) {
            notified.clear();
            return;
        }
        int maxPoints = maxPoints();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            List<String> ids = new ArrayList<>();
            List<Component> names = new ArrayList<>();
            for (Legendaries.Weapon weapon : disabledSlots(player).values()) {
                ids.add(weapon.id());
                names.add(Component.text(weapon.name(), weapon.color()));
            }

            List<String> before = notified.getOrDefault(player.getUniqueId(), List.of());
            if (ids.equals(before)) continue;

            if (ids.isEmpty()) {
                notified.remove(player.getUniqueId());
                player.sendMessage(Component.text("Weapon limit: all your weapons are active again.", NamedTextColor.GREEN));
            } else {
                notified.put(player.getUniqueId(), ids);
                player.sendMessage(Component.text("Weapon limit (" + maxPoints + (maxPoints == 1 ? " point" : " points")
                        + "): ", NamedTextColor.RED)
                        .append(Component.join(JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)), names))
                        .append(Component.text(ids.size() == 1 ? " is disabled." : " are disabled.", NamedTextColor.RED)));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        notified.remove(id);
        if (!plugin.isEnabled()) return;
        // The pickup order is kept for a while, so logging out and back in cannot reshuffle which weapon goes over
        // the limit. It must not be kept forever for players who never come back.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getServer().getPlayer(id) == null) acquired.remove(id);
        }, FORGET_PICKUP_ORDER_TICKS);
    }
}
