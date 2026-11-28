package net.rose.customPlugin.api;

import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.util.CooldownBars;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for a weapon's listener. It already does what every weapon listener needs:
 * <ul>
 *   <li>the offhand key (F) with the weapon in the main hand calls {@link #onOffhand} and cancels the swap;</li>
 *   <li>cooldown boss bars are hidden on quit and restored on join ({@link #onPlayerJoin} runs after that);</li>
 *   <li>every {@link PrimedAbility} made with {@link #primedAbility} is cancelled when the weapon is dropped or the
 *       player dies, and forgotten when they quit;</li>
 *   <li>{@link #onPlayerDeath} and {@link #onPlayerQuit} let a weapon clear its own per-player state.</li>
 * </ul>
 * Add your own {@code @EventHandler} methods for anything else (hits, shots, right-clicks).
 *
 * <p>Per-player state: keep it in a map keyed by UUID and remove the entry in {@link #onPlayerQuit} (and
 * {@link #onPlayerDeath} where it should not survive death). Anything that is not cleared there leaks.
 */
public abstract class AbilityListener<W extends CustomWeapon> implements Listener {

    protected final JavaPlugin plugin;
    protected final CooldownBars cooldowns;
    protected final W weapon;
    private final List<PrimedAbility> primedAbilities = new ArrayList<>();

    protected AbilityListener(WeaponContext context, W weapon) {
        this.plugin = context.plugin();
        this.cooldowns = context.cooldowns();
        this.weapon = weapon;
    }

    /** F pressed with the weapon in the main hand. The swap is cancelled for you. */
    protected void onOffhand(Player player, boolean sneaking) {
    }

    /** Whether F with the weapon in hand is used by this weapon (and so cancelled). Default true. */
    protected boolean usesOffhand() {
        return true;
    }

    /** The player joined. Cooldown boss bars are already restored; use this to show anything else the weapon needs. */
    protected void onPlayerJoin(Player player) {
    }

    /** The weapon was dropped. Primed abilities are already cancelled; use this to clear anything else it had going. */
    protected void onWeaponDropped(Player player) {
    }

    /** The player died: clear anything that should not survive death (primed state, stored charge). */
    protected void onPlayerDeath(Player player) {
    }

    /** The player left: clear their per-player state. Cooldowns are handled for you. */
    protected void onPlayerQuit(Player player) {
    }

    // ---------------------------------------------------------------- Helpers

    /** A key in this plugin's namespace, for tagging arrows, entities or items. */
    protected final NamespacedKey key(String name) {
        return new NamespacedKey(plugin, name);
    }

    /** True if the ability is ready. The cooldown boss bar already shows the time left, so no message is needed. */
    protected final boolean ready(Player player, String cooldownId) {
        return !cooldowns.isOnCooldown(player, cooldownId);
    }

    /**
     * An ability armed first and spent by the next hit, shot or throw (see {@link PrimedAbility}). Make it in the
     * constructor; its lifecycle (drop, death, quit) is handled by this class.
     */
    protected final PrimedAbility primedAbility(String id, String label, BossBar.Color color, long cooldownMs) {
        PrimedAbility ability = new PrimedAbility(cooldowns, id, label, color, cooldownMs);
        primedAbilities.add(ability);
        return ability;
    }

    protected final void startCooldown(Player player, String cooldownId, String label, BossBar.Color color,
                                       long durationMs) {
        cooldowns.start(player, cooldownId, label, color, durationMs);
    }

    // ------------------------------------------------------------- Boilerplate

    @EventHandler(ignoreCancelled = true)
    public final void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!usesOffhand()) return;
        Player player = event.getPlayer();
        if (!weapon.isWeapon(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);
        onOffhand(player, player.isSneaking());
    }

    @EventHandler
    public final void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        cooldowns.resume(player);
        onPlayerJoin(player);
    }

    /** Dropping the weapon cancels anything armed, so no boss bar is left behind. */
    @EventHandler(ignoreCancelled = true)
    public final void onDrop(PlayerDropItemEvent event) {
        if (!weapon.isWeapon(event.getItemDrop().getItemStack())) return;
        Player player = event.getPlayer();
        for (PrimedAbility ability : primedAbilities) ability.cancel(player);
        onWeaponDropped(player);
    }

    @EventHandler
    public final void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        for (PrimedAbility ability : primedAbilities) ability.cancel(player);
        onPlayerDeath(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public final void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (PrimedAbility ability : primedAbilities) ability.forget(player);
        onPlayerQuit(player);
        cooldowns.hideAll(player);
    }
}
