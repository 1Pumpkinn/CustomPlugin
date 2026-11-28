package net.rose.customPlugin.listener.sword;

import net.rose.customPlugin.api.RightClick;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.api.AbilityListener;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.effect.PetalEffect;
import net.rose.customPlugin.item.sword.PetalBlade;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class PetalBladeListener extends AbilityListener<PetalBlade> implements PetalEffect.Callbacks {

    private static final String PETALS_ID = "petal_blade";
    private static final long PETALS_COOLDOWN_MS = 30_000;

    private final PetalEffect petals;

    public PetalBladeListener(WeaponContext context, PetalBlade blade, PetalEffect petals) {
        super(context, blade);
        this.petals = petals;
    }

    /** Offhand key (F) while holding the Petal Blade summons the petals. The swap itself is always cancelled. */
    @Override
    protected void onOffhand(Player player, boolean sneaking) {
        summon(player);
    }

    // Not ignoreCancelled: a click on air has no block to use, so Bukkit reports it as cancelled from the start.
    // What matters is whether another plugin denied using the item.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (!RightClick.isUse(event) || event.getHand() != EquipmentSlot.HAND) return;
        if (!weapon.isWeapon(event.getItem())) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (!petals.isActive(player)) return;

        if (RightClick.yieldsToBlock(event)) return; // chests, doors, buttons... still work like normal
        event.setCancelled(true);
        petals.throwPetal(player);
    }

    @Override
    protected void onPlayerDeath(Player player) {
        petals.cancel(player);
        cooldowns.unprime(player, PETALS_ID);
    }

    /** Dropping the blade ends the petals and takes the boss bar with it (the cooldown still counts). */
    @Override
    protected void onWeaponDropped(Player player) {
        if (!petals.isActive(player)) return;

        petals.cancel(player);
        cooldowns.unprime(player, PETALS_ID);
        cooldowns.start(player, PETALS_ID, "Petal Blade", BossBar.Color.PINK, PETALS_COOLDOWN_MS);
    }

    /** Leaving with petals out still costs the cooldown, so a relog can't be used to skip it. */
    @Override
    protected void onPlayerQuit(Player player) {
        if (petals.isActive(player)) {
            petals.cancel(player);
            cooldowns.start(player, PETALS_ID, "Petal Blade", BossBar.Color.PINK, PETALS_COOLDOWN_MS);
        }
    }

    @Override
    public void onPetalsChanged(Player owner, int remaining) {
        cooldowns.updatePrimed(owner, PETALS_ID, primedTitle(remaining), (float) remaining / PetalEffect.PETAL_COUNT);
    }

    @Override
    public void onSwarmEnded(Player owner) {
        cooldowns.unprime(owner, PETALS_ID);
        cooldowns.start(owner, PETALS_ID, "Petal Blade", BossBar.Color.PINK, PETALS_COOLDOWN_MS);
    }

    private void summon(Player player) {
        if (cooldowns.isOnCooldown(player, PETALS_ID)) return; // boss bar already shows the time left
        if (petals.isActive(player)) return;

        petals.summon(player, this);
        cooldowns.prime(player, PETALS_ID, "Petal Blade", BossBar.Color.PINK);
        cooldowns.updatePrimed(player, PETALS_ID, primedTitle(PetalEffect.PETAL_COUNT), 1f);
    }

    private static Component primedTitle(int remaining) {
        return Component.text("Petal Blade - " + remaining + "/" + PetalEffect.PETAL_COUNT + " petals", NamedTextColor.WHITE);
    }
}
