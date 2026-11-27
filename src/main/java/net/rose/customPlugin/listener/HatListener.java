package net.rose.customPlugin.listener;

import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.effect.HatEffects;
import net.rose.customPlugin.item.Hat;
import net.rose.customPlugin.util.CooldownBars;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class HatListener implements Listener {

    private static final long PAD_COOLDOWN_MS = 20_000;
    private static final long RAZOR_COOLDOWN_MS = 25_000;
    private static final long RAZOR_MISS_COOLDOWN_MS = 8_000; // shorter when the razor hat hits nobody

    private static final String PAD_ID = "hat_bounce";
    private static final String RAZOR_ID = "hat_razor";

    private final Hat hat;
    private final CooldownBars cooldowns;
    private final HatEffects effects;

    public HatListener(Hat hat, CooldownBars cooldowns, HatEffects effects) {
        this.hat = hat;
        this.cooldowns = cooldowns;
        this.effects = effects;
    }

    /** Offhand key (F) while holding the Hat: throw the bounce hat, or the razor hat when crouching. The swap is always cancelled. */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!hat.isHat(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);
        if (effects.hasThrown(player)) return; // only one hat out at a time
        if (player.isSneaking()) {
            throwRazor(player);
        } else {
            throwPad(player);
        }
    }

    private void throwPad(Player player) {
        if (cooldowns.isOnCooldown(player, PAD_ID)) return; // boss bar already shows the time left
        cooldowns.start(player, PAD_ID, "Bounce Hat", BossBar.Color.GREEN, PAD_COOLDOWN_MS);
        effects.throwPad(player);
    }

    private void throwRazor(Player player) {
        if (cooldowns.isOnCooldown(player, RAZOR_ID)) return;
        cooldowns.start(player, RAZOR_ID, "Razor Hat", BossBar.Color.RED, RAZOR_COOLDOWN_MS);
        effects.throwRazor(player, () ->
                cooldowns.start(player, RAZOR_ID, "Razor Hat", BossBar.Color.RED, RAZOR_MISS_COOLDOWN_MS));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        effects.cancel(event.getEntity());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cooldowns.resume(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        effects.cancel(event.getPlayer());
        cooldowns.hideAll(event.getPlayer());
    }
}
