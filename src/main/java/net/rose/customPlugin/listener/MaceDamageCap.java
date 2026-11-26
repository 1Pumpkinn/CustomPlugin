package net.rose.customPlugin.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;

/**
 * Caps what a single mace hit (smash bonus included) can deal to any target, players and mobs.
 * Method from peroxide-dev/macedamagecap: when the hit is over the cap, the reduction modifiers
 * (armor, blocking, magic, absorption) are zeroed and the base damage is set to the cap, so the target
 * loses exactly the cap. Everything else about the hit stays vanilla.
 */
@SuppressWarnings("deprecation")
public final class MaceDamageCap implements Listener {

    private static final double DAMAGE_CAP = 8.0; // 4 hearts

    private static final DamageModifier[] REDUCTION_MODIFIERS = {
            DamageModifier.ARMOR,
            DamageModifier.BLOCKING,
            DamageModifier.MAGIC,
            DamageModifier.ABSORPTION
    };

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaceHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (attacker.getInventory().getItemInMainHand().getType() != Material.MACE) return;

        if (event.getDamage(DamageModifier.BASE) <= DAMAGE_CAP) return;
        // A heavily armored target can already be taking less than the cap: never raise that.
        if (event.getFinalDamage() <= DAMAGE_CAP) return;

        for (DamageModifier modifier : REDUCTION_MODIFIERS) {
            try {
                event.setDamage(modifier, 0);
            } catch (UnsupportedOperationException ignored) {
                // this modifier does not apply to the event
            }
        }
        event.setDamage(DAMAGE_CAP);
    }
}
