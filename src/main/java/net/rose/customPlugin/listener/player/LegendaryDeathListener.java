package net.rose.customPlugin.listener.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.rose.customPlugin.item.Legendaries;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Tells the whole server when a player dies carrying one of the custom weapons. */
public final class LegendaryDeathListener implements Listener {

    private final JavaPlugin plugin;
    private final Legendaries legendaries;

    public LegendaryDeathListener(JavaPlugin plugin, Legendaries legendaries) {
        this.plugin = plugin;
        this.legendaries = legendaries;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        List<Component> carried = legendaries.carriedBy(victim);
        if (carried.isEmpty()) return;

        Component message = Component.text()
                .append(Component.text("LEGENDARY ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(victim.name().color(NamedTextColor.WHITE))
                .append(Component.text(" died with the ", NamedTextColor.GRAY))
                .append(Component.join(JoinConfiguration.separators(
                        Component.text(", ", NamedTextColor.GRAY), Component.text(" and ", NamedTextColor.GRAY)), carried))
                .append(Component.text("!", NamedTextColor.GRAY))
                .build();

        // One tick later, so it lands under the vanilla death message instead of above it.
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getServer().broadcast(message));
    }
}
