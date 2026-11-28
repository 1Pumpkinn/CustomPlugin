package net.rose.customPlugin.api;

import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.util.CooldownBars;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An ability that is armed first (usually with the offhand key) and spent by the next hit, shot or throw, after which
 * a cooldown runs. It keeps who is armed and drives the boss bar: "ready" while armed, then the cooldown.
 *
 * <p>Make one with {@link AbilityListener#primedAbility}. The base listener then clears it for you when the weapon is
 * dropped or the player dies or quits, so the weapon only decides when to {@link #prime} and when to {@link #spend}.
 */
public final class PrimedAbility {

    private final CooldownBars cooldowns;
    private final String id;
    private final String label;
    private final BossBar.Color color;
    private final long cooldownMs;
    private final Set<UUID> armed = new HashSet<>();

    PrimedAbility(CooldownBars cooldowns, String id, String label, BossBar.Color color, long cooldownMs) {
        this.cooldowns = cooldowns;
        this.id = id;
        this.label = label;
        this.color = color;
        this.cooldownMs = cooldownMs;
    }

    /**
     * Arms the ability. False (and nothing changes) if it is on cooldown (the bar already shows the time left) or
     * already armed.
     */
    public boolean prime(Player player) {
        if (cooldowns.isOnCooldown(player, id)) return false;
        if (!armed.add(player.getUniqueId())) return false;
        cooldowns.prime(player, id, label, color);
        return true;
    }

    public boolean isPrimed(Player player) {
        return armed.contains(player.getUniqueId());
    }

    /** Uses up the arming and starts the cooldown. False if the player was not armed. */
    public boolean spend(Player player) {
        if (!consume(player)) return false;
        startCooldown(player);
        return true;
    }

    /**
     * Uses up the arming without starting the cooldown yet, for an action that can fail afterwards: follow with
     * {@link #startCooldown} when it worked or {@link #restore} when it did not. False if the player was not armed.
     */
    public boolean consume(Player player) {
        if (!armed.remove(player.getUniqueId())) return false;
        cooldowns.unprime(player, id);
        return true;
    }

    public void startCooldown(Player player) {
        cooldowns.start(player, id, label, color, cooldownMs);
    }

    /** Puts the arming back after {@link #consume}, because the action it was spent on did not happen. */
    public void restore(Player player) {
        if (!armed.add(player.getUniqueId())) return;
        cooldowns.prime(player, id, label, color);
    }

    /** Drops the arming and its bar without a cooldown (the weapon was dropped, or the player died). */
    public void cancel(Player player) {
        if (armed.remove(player.getUniqueId())) cooldowns.unprime(player, id);
    }

    /** Forgets the player (they left). Their bars are hidden separately. */
    void forget(Player player) {
        armed.remove(player.getUniqueId());
    }
}
