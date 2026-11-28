package net.rose.customPlugin.item;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The order each player picked their weapons up in, oldest first (a weapon carried twice appears twice). The weapon
 * limit counts weapons from least to most expensive and uses this order to settle which of two equally expensive
 * weapons counts first. Kept in memory only: after a restart the order starts out as slot order.
 */
public final class PickupOrder {

    /** How long a player's order is remembered after they log out (30 minutes). */
    private static final long FORGET_TICKS = 30L * 60L * 20L;

    private final JavaPlugin plugin;
    private final Map<UUID, List<String>> acquired = new HashMap<>();

    public PickupOrder(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Updates the player's order (weapons they no longer carry drop out, new ones go on the end, in slot order if
     * several showed up at once) and returns, for each weapon id in {@code slotOrder}, its place in that order
     * (lower is older).
     */
    public int[] ages(UUID player, List<String> slotOrder) {
        List<String> order = acquired.computeIfAbsent(player, k -> new ArrayList<>());

        Map<String, Integer> carriedCount = new HashMap<>();
        for (String id : slotOrder) carriedCount.merge(id, 1, Integer::sum);

        // Forget what is no longer carried.
        Map<String, Integer> kept = new HashMap<>();
        for (Iterator<String> it = order.iterator(); it.hasNext(); ) {
            String id = it.next();
            int have = kept.getOrDefault(id, 0);
            if (have < carriedCount.getOrDefault(id, 0)) {
                kept.put(id, have + 1);
            } else {
                it.remove();
            }
        }

        // Whatever is carried beyond what was already known is new.
        Map<String, Integer> seen = new HashMap<>();
        for (String id : slotOrder) {
            if (seen.merge(id, 1, Integer::sum) > kept.getOrDefault(id, 0)) order.add(id);
        }

        // The n-th copy of a weapon takes the n-th place that weapon holds in the order.
        Map<String, Integer> nextCopy = new HashMap<>();
        int[] ages = new int[slotOrder.size()];
        for (int slot = 0; slot < slotOrder.size(); slot++) {
            String id = slotOrder.get(slot);
            int copy = nextCopy.merge(id, 1, Integer::sum);
            int found = 0;
            for (int i = 0; i < order.size(); i++) {
                if (order.get(i).equals(id) && ++found == copy) {
                    ages[slot] = i;
                    break;
                }
            }
        }
        if (order.isEmpty()) acquired.remove(player);
        return ages;
    }

    /**
     * The player left: their order is kept for a while, so logging out and back in cannot reshuffle which weapon
     * goes over the limit, but not forever for players who never come back.
     */
    public void forgetLater(UUID player) {
        if (!plugin.isEnabled()) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getServer().getPlayer(player) == null) acquired.remove(player);
        }, FORGET_TICKS);
    }

    public void clear() {
        acquired.clear();
    }
}
