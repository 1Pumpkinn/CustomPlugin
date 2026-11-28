package net.rose.customPlugin.listener.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.item.Legendaries;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Caps how many weapons a player can use at once. Every custom weapon in the inventory costs weapon points by tier
 * ({@code weapon-limit} in config.yml, shown to players by /weaponpoints). Slots are counted in order (hotbar,
 * the rest of the inventory, then the offhand) and a weapon that would take the total over
 * {@code weapon-limit.max-points} is disabled: it can't be used for its abilities or to attack.
 *
 * <p>Everything here runs at LOWEST priority, so the weapon listeners (which skip cancelled events) never see a
 * disabled weapon being used.
 */
public final class WeaponLimitListener implements Listener {

    public static final String BYPASS_PERMISSION = "customplugin.weaponlimit.bypass";

    private static final int OFFHAND_SLOT = 40;
    private static final int STORAGE_SLOTS = 36;
    private static final long CHECK_INTERVAL_TICKS = 20L;

    private final JavaPlugin plugin;
    private final Legendaries legendaries;
    private final boolean enabled;
    private final int maxPoints;
    private final int defaultTier;

    /** The weapons (by id) each online player was last told are disabled, so a message only goes out on a change. */
    private final Map<UUID, List<String>> notified = new HashMap<>();
    private final BukkitTask checkTask;

    public WeaponLimitListener(JavaPlugin plugin, Legendaries legendaries) {
        this.plugin = plugin;
        this.legendaries = legendaries;
        this.enabled = plugin.getConfig().getBoolean("weapon-limit.enabled", true);
        this.maxPoints = Math.max(0, plugin.getConfig().getInt("weapon-limit.max-points", 6));
        this.defaultTier = Math.max(1, plugin.getConfig().getInt("weapon-limit.default-tier", 1));
        this.checkTask = enabled
                ? plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkAll, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS)
                : null;
    }

    public void shutdown() {
        if (checkTask != null) checkTask.cancel();
        notified.clear();
    }

    // ---- limit logic ----------------------------------------------------------------------------------------------

    /** The tier a weapon is in ({@code weapon-limit.weapon-tiers.<id>}); weapons not listed use {@code default-tier}. */
    public int tierOf(Legendaries.Weapon weapon) {
        return Math.max(1, plugin.getConfig().getInt("weapon-limit.weapon-tiers." + weapon.id(), defaultTier));
    }

    /** What a tier costs ({@code weapon-limit.tier-points.<tier>}). */
    public int pointsForTier(int tier) {
        return Math.max(0, plugin.getConfig().getInt("weapon-limit.tier-points." + tier, 1));
    }

    public int pointsOf(Legendaries.Weapon weapon) {
        return pointsForTier(tierOf(weapon));
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

    /** What a player carries: points used by the active weapons, the limit, and every weapon in slot order. */
    public record Summary(int used, int max, List<Carried> weapons) {
    }

    /** Which custom weapon a stack is, or null. */
    public Legendaries.Weapon identify(ItemStack stack) {
        return legendaries.identify(stack);
    }

    public int maxPoints() {
        return maxPoints;
    }

    public Summary summarize(Player player) {
        List<Carried> carried = new ArrayList<>();
        boolean limited = enabled && !player.hasPermission(BYPASS_PERMISSION);

        PlayerInventory inventory = player.getInventory();
        int used = 0;
        for (int slot = 0; slot <= OFFHAND_SLOT; slot++) {
            if (slot >= STORAGE_SLOTS && slot < OFFHAND_SLOT) continue; // armor slots hold no weapons
            ItemStack stack = inventory.getItem(slot);
            Legendaries.Weapon weapon = legendaries.identify(stack);
            if (weapon == null) continue;

            int cost = pointsOf(weapon) * Math.max(1, stack.getAmount());
            boolean disabled = limited && used + cost > maxPoints;
            if (!disabled) used += cost;
            carried.add(new Carried(weapon, slot, cost, disabled));
        }
        return new Summary(used, maxPoints, carried);
    }

    /** Inventory slot -> weapon, for every weapon the player carries that is over the limit. */
    private Map<Integer, Legendaries.Weapon> disabledSlots(Player player) {
        Map<Integer, Legendaries.Weapon> disabled = new LinkedHashMap<>();
        if (!enabled) return disabled;
        for (Carried carried : summarize(player).weapons()) {
            if (carried.disabled()) disabled.put(carried.slot(), carried.weapon());
        }
        return disabled;
    }

    /** The weapon in that hand if it is disabled, otherwise null. */
    private Legendaries.Weapon disabledIn(Player player, EquipmentSlot hand) {
        if (!enabled) return null;
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return null;
        PlayerInventory inventory = player.getInventory();
        int slot = hand == EquipmentSlot.OFF_HAND ? OFFHAND_SLOT : inventory.getHeldItemSlot();
        // Cheap exit for the common case of a hand holding something that is not a weapon.
        if (legendaries.identify(inventory.getItem(slot)) == null) return null;
        return disabledSlots(player).get(slot);
    }

    private void tell(Player player, Legendaries.Weapon weapon) {
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
        notified.remove(event.getPlayer().getUniqueId());
    }
}
