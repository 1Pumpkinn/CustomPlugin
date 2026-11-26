package net.rose.customPlugin.util;

import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

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

    /** True once the entity can no longer be affected: an offline or dead player, or a removed or dead mob. */
    public static boolean isGone(LivingEntity entity) {
        if (entity instanceof Player player) return !player.isOnline() || player.isDead();
        return !entity.isValid() || entity.isDead();
    }

    /** Like {@link #isValidPlayer}, but mobs count too (not armor stands, and not the caster's own tamed pets). */
    public static boolean isValidTarget(Player caster, Entity entity) {
        if (entity instanceof Player player) return isValidPlayer(caster, player);
        if (!(entity instanceof LivingEntity living) || living.isDead() || entity instanceof ArmorStand) return false;
        return !(entity instanceof Tameable pet && caster.getUniqueId().equals(pet.getOwnerUniqueId()));
    }
}
