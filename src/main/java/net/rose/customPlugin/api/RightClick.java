package net.rose.customPlugin.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * The checks every "right-click to use the weapon" handler repeats. Register the handler at
 * {@code EventPriority.HIGH} <b>without</b> {@code ignoreCancelled}: a click on air has no block to use, so Bukkit
 * reports it as cancelled from the start, and what matters is whether another plugin denied using the item.
 *
 * <pre>{@code
 * if (!RightClick.isUse(event) || !weapon.isWeapon(event.getItem())) return;
 * if (RightClick.yieldsToBlock(event)) return; // let chests and doors work
 * event.setCancelled(true);
 * ...
 * }</pre>
 */
public final class RightClick {

    private RightClick() {
    }

    /** A right-click, in the main or offhand, that no other plugin has denied the use of the item for. */
    public static boolean isUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return false;
        if (event.useItemInHand() == Event.Result.DENY) return false;

        EquipmentSlot hand = event.getHand();
        return hand == EquipmentSlot.HAND || hand == EquipmentSlot.OFF_HAND;
    }

    /** The click is on a block that has a use of its own (chest, door, button...) and the player is not sneaking. */
    public static boolean yieldsToBlock(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        return event.getAction() == Action.RIGHT_CLICK_BLOCK
                && !player.isSneaking()
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType().isInteractable();
    }
}
