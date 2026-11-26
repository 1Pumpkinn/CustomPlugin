package net.rose.customPlugin.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cooldown between spear lunges, shown on the action bar when a lunge is blocked (no boss bar).
 *
 * <p>Paper's EntityLungeEvent only exists on newer servers (26.1+), so it is looked up by name and
 * registered reflectively. That keeps the plugin compiling against the 1.21.11 API; on a server
 * without the event it logs a warning and the cooldown does nothing.
 */
public final class LungeCooldown implements Listener {

    private static final String EVENT_CLASS = "io.papermc.paper.event.entity.EntityLungeEvent";
    private static final long LUNGE_COOLDOWN_MS = 10_000;

    private final JavaPlugin plugin;
    private final Map<UUID, Long> endsAt = new HashMap<>();

    public LungeCooldown(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Class<? extends Event> type;
        try {
            type = Class.forName(EVENT_CLASS).asSubclass(Event.class);
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("EntityLungeEvent not found on this server (needs Paper 26.1+): lunge cooldown disabled");
            return;
        }

        PluginManager manager = plugin.getServer().getPluginManager();
        manager.registerEvent(type, this, EventPriority.HIGH, (listener, event) -> check(event), plugin, true);
        manager.registerEvent(type, this, EventPriority.MONITOR, (listener, event) -> start(event), plugin, true);
    }

    private void check(Event event) {
        if (!(event instanceof EntityEvent entityEvent) || !(event instanceof Cancellable cancellable)) return;
        if (!(entityEvent.getEntity() instanceof Player player)) return;

        long remaining = remainingMillis(player);
        if (remaining <= 0) return;

        cancellable.setCancelled(true);
        player.sendActionBar(Component.text("Lunge on cooldown - " + (remaining + 999) / 1000 + "s", NamedTextColor.RED));
    }

    private void start(Event event) {
        if (!(event instanceof EntityEvent entityEvent)) return;
        if (!(entityEvent.getEntity() instanceof Player player)) return;

        endsAt.put(player.getUniqueId(), System.currentTimeMillis() + LUNGE_COOLDOWN_MS);
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
