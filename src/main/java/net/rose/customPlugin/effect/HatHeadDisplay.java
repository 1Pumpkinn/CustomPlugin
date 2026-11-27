package net.rose.customPlugin.effect;

import net.rose.customPlugin.item.misc.Hat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * While a player holds the Hat, it is drawn on their head instead of in their hand (the model hides itself in
 * the hand display slots). It is a plain item display that follows the player each tick; it is not mounted on
 * them, because a player with a passenger can't be teleported. Hidden while their thrown hat is out.
 */
public final class HatHeadDisplay {

    private static final Vector3f ON_HEAD = new Vector3f(0f, 0.075f, 0f); // hat center above the top of the head

    private final JavaPlugin plugin;
    private final Hat hat;
    private final HatEffects effects;
    private final Map<UUID, ItemDisplay> worn = new HashMap<>();
    private BukkitTask task;

    public HatHeadDisplay(JavaPlugin plugin, Hat hat, HatEffects effects) {
        this.plugin = plugin;
        this.hat = hat;
        this.effects = effects;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        worn.values().forEach(ItemDisplay::remove);
        worn.clear();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean show = !player.isDead()
                    && !player.isInvisible()
                    && player.getGameMode() != GameMode.SPECTATOR
                    && hat.isHat(player.getInventory().getItemInMainHand())
                    && !effects.hasThrown(player);

            ItemDisplay display = worn.get(player.getUniqueId());
            if (!show) {
                if (display != null) {
                    display.remove();
                    worn.remove(player.getUniqueId());
                }
                continue;
            }

            Location head = player.getLocation();
            head.add(0, player.getHeight(), 0);
            head.setPitch(0f);
            if (display == null || !display.isValid()) {
                display = effects.spawnStaticDisplay(head);
                HatEffects.pose(display, new Quaternionf(), new Vector3f(1f), ON_HEAD);
                worn.put(player.getUniqueId(), display);
            } else {
                display.teleport(head);
            }
        }

        // Players who left
        worn.entrySet().removeIf(entry -> {
            if (Bukkit.getPlayer(entry.getKey()) != null) return false;
            entry.getValue().remove();
            return true;
        });
    }
}
