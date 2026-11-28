package net.rose.customPlugin.listener.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The combat system ({@code combat} in config.yml). A player who hits another player, or is hit by one, is in combat
 * for {@code combat.duration-seconds} (30 by default), and while they are, they can't run commands. Ability damage
 * counts like a hit, so this works for every weapon: abilities deal their damage through the normal damage event
 * with the caster as the source. Hitting mobs does not put anyone in combat. Dying ends it.
 *
 * <p>Players with {@link #BYPASS_PERMISSION} are never put in combat and can always run commands.
 */
public final class CombatListener implements Listener {

    public static final String BYPASS_PERMISSION = "customplugin.combat.bypass";

    private static final long CHECK_INTERVAL_TICKS = 20L;

    private final JavaPlugin plugin;
    /** Player -> when their combat ends (System.currentTimeMillis). */
    private final Map<UUID, Long> combatEnds = new HashMap<>();
    /** Player -> the last player who hit them, so a kill ends the killer's combat even when the death has no killer. */
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    private final BukkitTask task;

    public CombatListener(JavaPlugin plugin) {
        this.plugin = plugin;
        // Always scheduled: enabled / duration are read from the config each time, so /customplugin reload applies them.
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick,
                CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    public void shutdown() {
        task.cancel();
        combatEnds.clear();
        lastAttacker.clear();
    }

    // ---- state ------------------------------------------------------------------------------------------------------

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("combat.enabled", true);
    }

    private long durationMillis() {
        return Math.max(1, plugin.getConfig().getInt("combat.duration-seconds", 30)) * 1000L;
    }

    /** Whether the player is in combat right now. */
    public boolean isInCombat(Player player) {
        return secondsLeft(player) > 0;
    }

    /** Whole seconds of combat the player has left, 0 if they are not in combat. */
    public int secondsLeft(Player player) {
        if (!isEnabled()) return 0;
        Long ends = combatEnds.get(player.getUniqueId());
        if (ends == null) return 0;
        long left = ends - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000.0);
    }

    /** Puts the player in combat (or restarts the timer). */
    public void tag(Player player) {
        if (!isEnabled() || player.hasPermission(BYPASS_PERMISSION)) return;
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return;

        boolean wasInCombat = isInCombat(player);
        combatEnds.put(player.getUniqueId(), System.currentTimeMillis() + durationMillis());
        if (!wasInCombat) {
            player.sendMessage(Component.text("You are in combat for " + (durationMillis() / 1000)
                    + " seconds. You can't use commands.", NamedTextColor.RED));
        }
    }

    /** Once a second: shows each player in combat their time left, and ends combat that has run out. */
    private void tick() {
        long now = System.currentTimeMillis();
        boolean actionBar = isEnabled() && plugin.getConfig().getBoolean("combat.action-bar", true);
        combatEnds.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (entry.getValue() > now) {
                if (actionBar && player != null) {
                    int left = (int) Math.ceil((entry.getValue() - now) / 1000.0);
                    player.sendActionBar(Component.text("In combat: ", NamedTextColor.RED)
                            .append(Component.text(left + "s", NamedTextColor.WHITE)));
                }
                return false;
            }
            if (player != null) announceEnd(player);
            lastAttacker.remove(entry.getKey());
            return true;
        });
    }

    private void announceEnd(Player player) {
        if (!isEnabled()) return;
        if (plugin.getConfig().getBoolean("combat.action-bar", true)) {
            player.sendActionBar(Component.text("You are no longer in combat", NamedTextColor.GREEN));
        }
        player.sendMessage(Component.text("You are no longer in combat.", NamedTextColor.GREEN));
    }

    /** Ends the player's combat right away. */
    private void end(Player player) {
        if (combatEnds.remove(player.getUniqueId()) != null) announceEnd(player);
    }

    // ---- tagging ----------------------------------------------------------------------------------------------------

    /**
     * MONITOR, so only hits that really landed count (a hit another plugin or the weapon limit cancelled does not).
     * The source is the entity that caused the damage, so arrows and ability damage count for whoever shot or cast them.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(event);
        if (attacker == null || attacker.equals(victim)) return;

        tag(victim);
        tag(attacker);
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
    }

    private static Player attackerOf(EntityDamageByEntityEvent event) {
        Entity cause = event.getDamageSource().getCausingEntity();
        if (cause instanceof Player player) return player;
        Entity damager = event.getDamager();
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    /** Dying ends the victim's combat, and killing a player ends the killer's. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) {
            UUID last = lastAttacker.get(victim.getUniqueId());
            if (last != null) killer = plugin.getServer().getPlayer(last);
        }
        combatEnds.remove(victim.getUniqueId());
        lastAttacker.remove(victim.getUniqueId());
        if (killer != null && !killer.equals(victim)) end(killer);
    }

    // ---- blocking ---------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        int left = secondsLeft(player);
        if (left <= 0 || player.hasPermission(BYPASS_PERMISSION)) return;
        if (isAllowed(event.getMessage())) return;

        event.setCancelled(true);
        player.sendMessage(Component.text("You can't use commands while in combat (" + left + "s left).",
                NamedTextColor.RED));
    }

    /** Whether the command (as typed, with its slash) is in {@code combat.allowed-commands}. */
    private boolean isAllowed(String message) {
        List<String> allowed = plugin.getConfig().getStringList("combat.allowed-commands");
        if (allowed.isEmpty()) return false;

        String label = message.startsWith("/") ? message.substring(1) : message;
        int space = label.indexOf(' ');
        if (space >= 0) label = label.substring(0, space);
        label = label.toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':'); // /minecraft:msg counts as msg
        if (colon >= 0) label = label.substring(colon + 1);

        for (String command : allowed) {
            if (command.replaceFirst("^/", "").toLowerCase(Locale.ROOT).equals(label)) return true;
        }
        return false;
    }
}
