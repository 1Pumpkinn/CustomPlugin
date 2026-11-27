package net.rose.customPlugin.listener.player;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 1 minute cooldown between enchanted golden apples, shown only as the greyed-out apple (no boss bar). */
public final class EgapListener implements Listener {

    private static final long EGAP_COOLDOWN_MS = 60_000;

    private final Map<UUID, Long> endsAt = new HashMap<>();

    /** Safety net (e.g. cooldown carried over a relog): don't let the apple be consumed. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onConsumeCheck(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.ENCHANTED_GOLDEN_APPLE) return;
        if (remainingMillis(event.getPlayer()) > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.ENCHANTED_GOLDEN_APPLE) return;

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        endsAt.values().removeIf(end -> end <= now); // players who left without coming back
        endsAt.put(player.getUniqueId(), now + EGAP_COOLDOWN_MS);
        // Vanilla cooldown greys out the apples and stops the player from starting to eat one.
        player.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, (int) (EGAP_COOLDOWN_MS / 50));
    }

    /** Vanilla item cooldowns don't survive a relog, so reapply what's left. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long remaining = remainingMillis(player);
        if (remaining > 0) {
            player.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, (int) Math.ceil(remaining / 50.0));
        }
    }

    private long remainingMillis(Player player) {
        Long end = endsAt.get(player.getUniqueId());
        if (end == null) return 0;
        long left = end - System.currentTimeMillis();
        if (left <= 0) {
            endsAt.remove(player.getUniqueId());
            return 0;
        }
        return left;
    }
}
