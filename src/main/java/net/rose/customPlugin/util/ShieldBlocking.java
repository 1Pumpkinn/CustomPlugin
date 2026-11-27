package net.rose.customPlugin.util;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.util.Vector;

/** Lets a raised shield stop a projectile-style ability hit, with the same 180 degree front arc as vanilla. */
public final class ShieldBlocking {

    private ShieldBlocking() {
    }

    /**
     * If {@code victim} is raising a shield and the attack (moving along {@code travelDirection}, attacker to
     * victim) comes from the front, plays the block sound, wears the shield down like vanilla would for
     * {@code damage}, and returns true. The caller then deals no damage.
     */
    public static boolean tryBlock(Player victim, Vector travelDirection, double damage) {
        if (!victim.isBlocking()) return false;

        Vector travel = travelDirection.clone().setY(0);
        Vector view = victim.getEyeLocation().getDirection().setY(0);
        boolean fromFront = travel.lengthSquared() < 1.0E-6
                || view.lengthSquared() < 1.0E-6
                || travel.dot(view) < 0.0;
        if (!fromFront) return false;

        victim.getWorld().playSound(victim.getLocation(), "item.shield.block", SoundCategory.PLAYERS, 1.0f, 1.0f);
        wear(victim, damage);
        return true;
    }

    /** Vanilla: a blocked hit of 3 or more damage costs the shield 1 + floor(damage) durability. */
    private static void wear(Player victim, double damage) {
        if (damage < 3.0) return;
        EquipmentSlot hand = victim.getActiveItemHand();
        PlayerInventory inventory = victim.getInventory();
        ItemStack shield = inventory.getItem(hand);
        if (shield == null || !(shield.getItemMeta() instanceof Damageable meta)) return;

        int worn = meta.getDamage() + 1 + (int) Math.floor(damage);
        int max = meta.hasMaxDamage() ? meta.getMaxDamage() : shield.getType().getMaxDurability();
        if (max > 0 && worn >= max) {
            inventory.setItem(hand, null);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
        } else {
            meta.setDamage(worn);
            shield.setItemMeta(meta);
        }
    }
}
