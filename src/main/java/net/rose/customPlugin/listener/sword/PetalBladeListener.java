package net.rose.customPlugin.listener.sword;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.effect.PetalEffect;
import net.rose.customPlugin.item.sword.PetalBlade;
import net.rose.customPlugin.util.CooldownBars;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public final class PetalBladeListener implements Listener, PetalEffect.Callbacks {

    private static final String PETALS_ID = "petal_blade";
    private static final long PETALS_COOLDOWN_MS = 30_000;

    private final JavaPlugin plugin;
    private final PetalBlade blade;
    private final CooldownBars cooldowns;
    private final PetalEffect petals;

    public PetalBladeListener(JavaPlugin plugin, PetalBlade blade, CooldownBars cooldowns, PetalEffect petals) {
        this.plugin = plugin;
        this.blade = blade;
        this.cooldowns = cooldowns;
        this.petals = petals;
    }

    /** Offhand key (F) while holding the Petal Blade summons the petals. The swap itself is always cancelled. */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!blade.isPetalBlade(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);
        summon(player);
    }

    // Not ignoreCancelled: a click on air has no block to use, so Bukkit reports it as cancelled from the start.
    // What matters is whether another plugin denied using the item.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.useItemInHand() == Event.Result.DENY) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!blade.isPetalBlade(event.getItem())) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (!petals.isActive(player)) return;

        // Chests, doors, buttons... still work like normal, unless the player is sneaking.
        if (action == Action.RIGHT_CLICK_BLOCK
                && !player.isSneaking()
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType().isInteractable()) {
            return;
        }
        event.setCancelled(true);
        petals.throwPetal(player);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        petals.cancel(player);
        cooldowns.unprime(player, PETALS_ID);
    }

    /** Dropping the blade ends the petals and takes the boss bar with it (the cooldown still counts). */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!blade.isPetalBlade(event.getItemDrop().getItemStack())) return;
        Player player = event.getPlayer();
        if (!petals.isActive(player)) return;

        petals.cancel(player);
        cooldowns.unprime(player, PETALS_ID);
        cooldowns.start(player, PETALS_ID, "Petal Blade", BossBar.Color.PINK, PETALS_COOLDOWN_MS);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cooldowns.resume(event.getPlayer());
    }

    /** Leaving with petals out still costs the cooldown, so a relog can't be used to skip it. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (petals.isActive(player)) {
            petals.cancel(player);
            cooldowns.start(player, PETALS_ID, "Petal Blade", BossBar.Color.PINK, PETALS_COOLDOWN_MS);
        }
        cooldowns.hideAll(player);
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
