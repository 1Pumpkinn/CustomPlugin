package net.rose.customPlugin.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * A weapon taken out of a hand so it can be thrown, and the slot it came from. If the throw then fails, the weapon is
 * put straight back, so it is never lost.
 *
 * <pre>{@code
 * HeldWeapon held = HeldWeapon.take(player, hand, weapon);
 * if (held == null) return;
 * try {
 *     launch(held.item(), held.slot());
 * } catch (Throwable t) {
 *     held.restoreAfterFailure(player, plugin, "Zeus Disc", t);
 * }
 * }</pre>
 */
public record HeldWeapon(ItemStack item, int slot) {

    public static final int OFFHAND_SLOT = 40;

    /**
     * Takes the weapon out of the given hand (the whole stack leaves the slot; {@link #item()} is a single copy).
     * Null, and nothing taken, if that hand does not hold the weapon.
     */
    public static HeldWeapon take(Player player, EquipmentSlot hand, CustomWeapon weapon) {
        PlayerInventory inventory = player.getInventory();
        int slot = hand == EquipmentSlot.OFF_HAND ? OFFHAND_SLOT : inventory.getHeldItemSlot();
        ItemStack held = inventory.getItem(slot);
        if (!weapon.isWeapon(held)) return null;

        ItemStack thrown = held.clone();
        thrown.setAmount(1);
        inventory.setItem(slot, null);
        return new HeldWeapon(thrown, slot);
    }

    /** Puts the weapon back in the slot it was taken from. */
    public void restore(Player player) {
        player.getInventory().setItem(slot, item);
    }

    /** Logs why the throw failed, then puts the weapon back. */
    public void restoreAfterFailure(Player player, JavaPlugin plugin, String weaponName, Throwable cause) {
        plugin.getLogger().log(Level.SEVERE, "Could not throw the " + weaponName, cause);
        restore(player);
    }
}
