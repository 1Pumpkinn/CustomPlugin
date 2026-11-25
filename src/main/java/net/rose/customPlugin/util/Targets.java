package net.rose.customPlugin.util;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class Targets {

    private Targets() {
    }

    /** A living, non-creative/spectator player who is not the caster. */
    public static boolean isValidPlayer(Player caster, Entity entity) {
        if (!(entity instanceof Player player)) return false;
        if (player.equals(caster) || player.isDead()) return false;
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }
}
