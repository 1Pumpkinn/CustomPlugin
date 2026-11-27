package net.rose.customPlugin.listener.player;

import net.rose.customPlugin.item.misc.KillToken;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class KillTokenListener implements Listener {

    private final KillToken killToken;

    public KillTokenListener(KillToken killToken) {
        this.killToken = killToken;
    }

    /**
     * Drops a kill token on the ground where the victim died.
     * It is not placed in anyone's inventory; players pick it up like any other item.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        victim.getWorld().dropItemNaturally(victim.getLocation(), killToken.create());
    }
}
