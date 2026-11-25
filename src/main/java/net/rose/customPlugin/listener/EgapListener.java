package net.rose.customPlugin.listener;

import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.util.CooldownBars;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** 1 minute cooldown between enchanted golden apples, shown as a boss bar. */
public final class EgapListener implements Listener {

    private static final String EGAP_ID = "egap";
    private static final long EGAP_COOLDOWN_MS = 60_000;

    private final CooldownBars cooldowns;

    public EgapListener(CooldownBars cooldowns) {
        this.cooldowns = cooldowns;
    }

    /** Safety net (e.g. cooldown carried over a relog): don't let the apple be consumed. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onConsumeCheck(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.ENCHANTED_GOLDEN_APPLE) return;
        if (cooldowns.isOnCooldown(event.getPlayer(), EGAP_ID)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.ENCHANTED_GOLDEN_APPLE) return;

        Player player = event.getPlayer();
        cooldowns.start(player, EGAP_ID, "Egap", BossBar.Color.PURPLE, EGAP_COOLDOWN_MS);
        // Vanilla cooldown greys out the apples and stops the player from starting to eat one.
        player.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, (int) (EGAP_COOLDOWN_MS / 50));
    }

    /** Vanilla item cooldowns don't survive a relog, so reapply what's left. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long remaining = cooldowns.remainingMillis(player, EGAP_ID);
        if (remaining > 0) {
            player.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, (int) Math.ceil(remaining / 50.0));
        }
    }
}
