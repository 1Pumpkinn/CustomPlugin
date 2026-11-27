package net.rose.customPlugin.util;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks per-player ability cooldowns and shows each active one as a boss bar
 * that drains while the cooldown runs. Cooldowns survive relog; the bar is
 * hidden on quit and restored on join.
 */
public final class CooldownBars {

    private static final long UPDATE_TICKS = 2L;

    private final JavaPlugin plugin;
    private final Map<UUID, Map<String, Cooldown>> cooldowns = new HashMap<>();
    private final Map<UUID, Map<String, BossBar>> primed = new HashMap<>();

    public CooldownBars(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Creative players bypass every ability cooldown: nothing is ever put on cooldown for them. */
    private static boolean bypasses(Player player) {
        return player.getGameMode() == GameMode.CREATIVE;
    }

    /** Starts (or restarts) a cooldown and shows its boss bar. Creative players never get one. */
    public void start(Player player, String id, String label, BossBar.Color color, long durationMs) {
        if (bypasses(player)) return;
        Map<String, Cooldown> perPlayer = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        Cooldown previous = perPlayer.remove(id);
        if (previous != null) hide(player, previous);

        Cooldown cooldown = new Cooldown(id, label, color, durationMs, System.currentTimeMillis() + durationMs);
        perPlayer.put(id, cooldown);
        show(player, cooldown);
    }

    public boolean isOnCooldown(Player player, String id) {
        if (bypasses(player)) return false;
        Map<String, Cooldown> perPlayer = cooldowns.get(player.getUniqueId());
        if (perPlayer == null) return false;
        Cooldown cooldown = perPlayer.get(id);
        return cooldown != null && cooldown.endsAt > System.currentTimeMillis();
    }

    /** Milliseconds left on the cooldown, or 0 if it isn't running. */
    public long remainingMillis(Player player, String id) {
        if (bypasses(player)) return 0;
        Map<String, Cooldown> perPlayer = cooldowns.get(player.getUniqueId());
        if (perPlayer == null) return 0;
        Cooldown cooldown = perPlayer.get(id);
        return cooldown == null ? 0 : Math.max(0, cooldown.endsAt - System.currentTimeMillis());
    }

    /** Shows a full "ready" boss bar for an ability that is primed and waiting to be used. */
    public void prime(Player player, String id, String label, BossBar.Color color) {
        unprime(player, id);
        BossBar bar = BossBar.bossBar(Component.text(label + " - ready", NamedTextColor.WHITE), 1f,
                color, BossBar.Overlay.PROGRESS);
        primed.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(id, bar);
        player.showBossBar(bar);
    }

    /** Changes the text and fill of a primed bar, e.g. to count down uses left. Does nothing if it isn't showing. */
    public void updatePrimed(Player player, String id, Component title, float progress) {
        Map<String, BossBar> perPlayer = primed.get(player.getUniqueId());
        if (perPlayer == null) return;
        BossBar bar = perPlayer.get(id);
        if (bar == null) return;
        bar.name(title);
        bar.progress(Math.max(0f, Math.min(1f, progress)));
    }

    /** Hides the primed bar once the ability has been used (or lost). */
    public void unprime(Player player, String id) {
        Map<String, BossBar> perPlayer = primed.get(player.getUniqueId());
        if (perPlayer == null) return;
        BossBar bar = perPlayer.remove(id);
        if (bar != null) player.hideBossBar(bar);
        if (perPlayer.isEmpty()) primed.remove(player.getUniqueId());
    }

    /** Hides all of the player's bars (e.g. on quit) but keeps the cooldowns running. Primed bars are dropped. */
    public void hideAll(Player player) {
        UUID id = player.getUniqueId();
        Map<String, BossBar> primedBars = primed.remove(id);
        if (primedBars != null) {
            for (BossBar bar : primedBars.values()) {
                player.hideBossBar(bar);
            }
        }
        Map<String, Cooldown> perPlayer = cooldowns.get(id);
        if (perPlayer == null) return;

        long now = System.currentTimeMillis();
        long latestEnd = now;
        Iterator<Cooldown> it = perPlayer.values().iterator();
        while (it.hasNext()) {
            Cooldown cooldown = it.next();
            hide(player, cooldown);
            if (cooldown.endsAt <= now) {
                it.remove();
            } else {
                latestEnd = Math.max(latestEnd, cooldown.endsAt);
            }
        }

        if (perPlayer.isEmpty()) {
            cooldowns.remove(id);
        } else if (plugin.isEnabled()) {
            // Running cooldowns survive a relog, but a player who never comes back must not keep them forever.
            long delayTicks = (latestEnd - now) / 50L + 2L;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> forgetExpired(id), delayTicks);
        }
    }

    /** Drops the finished cooldowns of a player who is still offline. */
    private void forgetExpired(UUID id) {
        if (Bukkit.getPlayer(id) != null) return;
        Map<String, Cooldown> perPlayer = cooldowns.get(id);
        if (perPlayer == null) return;
        long now = System.currentTimeMillis();
        perPlayer.values().removeIf(cooldown -> cooldown.endsAt <= now);
        if (perPlayer.isEmpty()) cooldowns.remove(id);
    }

    /** Re-shows bars for any cooldowns that are still running (e.g. on join). */
    public void resume(Player player) {
        Map<String, Cooldown> perPlayer = cooldowns.get(player.getUniqueId());
        if (perPlayer == null) return;

        long now = System.currentTimeMillis();
        Iterator<Cooldown> it = perPlayer.values().iterator();
        while (it.hasNext()) {
            Cooldown cooldown = it.next();
            if (cooldown.endsAt <= now) {
                it.remove();
            } else {
                show(player, cooldown);
            }
        }
        if (perPlayer.isEmpty()) cooldowns.remove(player.getUniqueId());
    }

    /** Hides every bar for online players; call from onDisable. */
    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            hideAll(player);
        }
    }

    // ---------------------------------------------------------------- Internals

    private void show(Player player, Cooldown cooldown) {
        hide(player, cooldown);

        long remaining = Math.max(0, cooldown.endsAt - System.currentTimeMillis());
        cooldown.bar = BossBar.bossBar(title(cooldown, remaining), progress(cooldown, remaining),
                cooldown.color, BossBar.Overlay.PROGRESS);
        player.showBossBar(cooldown.bar);

        cooldown.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    hide(player, cooldown);
                    return;
                }

                long left = cooldown.endsAt - System.currentTimeMillis();
                if (left <= 0) {
                    finish(player, cooldown);
                    return;
                }
                cooldown.bar.name(title(cooldown, left));
                cooldown.bar.progress(progress(cooldown, left));
            }
        }.runTaskTimer(plugin, UPDATE_TICKS, UPDATE_TICKS);
    }

    private void hide(Player player, Cooldown cooldown) {
        if (cooldown.task != null) {
            cooldown.task.cancel();
            cooldown.task = null;
        }
        if (cooldown.bar != null) {
            player.hideBossBar(cooldown.bar);
            cooldown.bar = null;
        }
    }

    private void finish(Player player, Cooldown cooldown) {
        hide(player, cooldown);
        Map<String, Cooldown> perPlayer = cooldowns.get(player.getUniqueId());
        if (perPlayer == null) return;
        perPlayer.remove(cooldown.id);
        if (perPlayer.isEmpty()) cooldowns.remove(player.getUniqueId());
    }

    private static Component title(Cooldown cooldown, long remainingMs) {
        long seconds = (remainingMs + 999) / 1000;
        return Component.text(cooldown.label + " - " + seconds + "s", NamedTextColor.WHITE);
    }

    private static float progress(Cooldown cooldown, long remainingMs) {
        return Math.max(0f, Math.min(1f, (float) remainingMs / cooldown.totalMs));
    }

    private static final class Cooldown {
        private final String id;
        private final String label;
        private final BossBar.Color color;
        private final long totalMs;
        private final long endsAt;
        private BossBar bar;
        private BukkitTask task;

        private Cooldown(String id, String label, BossBar.Color color, long totalMs, long endsAt) {
            this.id = id;
            this.label = label;
            this.color = color;
            this.totalMs = totalMs;
            this.endsAt = endsAt;
        }
    }
}
