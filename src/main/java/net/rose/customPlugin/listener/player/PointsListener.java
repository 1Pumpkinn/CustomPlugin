package net.rose.customPlugin.listener.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.shop.Points;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Players earn points ({@code points.per-kill} in config.yml) for killing other players, abilities included. */
public final class PointsListener implements Listener {

    private final JavaPlugin plugin;
    private final Points points;

    public PointsListener(JavaPlugin plugin, Points points) {
        this.plugin = plugin;
        this.points = points;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        int reward = plugin.getConfig().getInt("points.per-kill", 1);
        if (reward <= 0) return;

        int total = points.add(killer.getUniqueId(), reward);
        killer.sendMessage(Component.text("+" + reward + (reward == 1 ? " point" : " points")
                + " (" + total + " total)", NamedTextColor.GOLD));
    }
}
