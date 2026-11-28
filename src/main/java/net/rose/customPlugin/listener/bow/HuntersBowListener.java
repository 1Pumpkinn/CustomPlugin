package net.rose.customPlugin.listener.bow;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.api.AbilityListener;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.item.bow.HuntersBow;
import net.rose.customPlugin.util.Targets;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Hunter's Bow. Highlighting is the glowing effect: the target gets an outline that everyone can see, through walls.
 * <ul>
 *   <li>Passive: a player or mob hit by an arrow from this bow is highlighted.</li>
 *   <li>Offhand (F): highlights every player and mob near the shooter.</li>
 * </ul>
 */
public final class HuntersBowListener extends AbilityListener<HuntersBow> {

    private static final int HIT_HIGHLIGHT_TICKS = 200;       // 10s on whatever the bow hit
    private static final double PULSE_RANGE = 15.0;           // blocks around the shooter
    private static final int PULSE_HIGHLIGHT_TICKS = 160;     // 8s on everything the offhand reveals

    private static final long PULSE_COOLDOWN_MS = 30_000;
    private static final String PULSE_ID = "hunters_pulse";

    private final NamespacedKey arrowKey = key("hunters_arrow");

    public HuntersBowListener(WeaponContext context, HuntersBow weapon) {
        super(context, weapon);
    }

    /** F with the bow in hand: highlight everything nearby. The swap is already cancelled by the base class. */
    @Override
    protected void onOffhand(Player player, boolean sneaking) {
        pulse(player);
    }

    /** Arrows shot from this bow are tagged, so the passive knows them when they land. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!weapon.isWeapon(event.getBow())) return;
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;

        arrow.getPersistentDataContainer().set(arrowKey, PersistentDataType.BYTE, (byte) 1);
    }

    /** Passive: MONITOR, so only an arrow that really damaged the target (not one a shield or another plugin stopped) counts. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow)) return;
        if (!arrow.getPersistentDataContainer().has(arrowKey, PersistentDataType.BYTE)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;
        if (!(event.getEntity() instanceof LivingEntity victim) || !Targets.isValidTarget(shooter, victim)) return;

        highlight(victim, HIT_HIGHLIGHT_TICKS);
        shooter.playSound(shooter.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.7f);
    }

    private void pulse(Player player) {
        if (cooldowns.isOnCooldown(player, PULSE_ID)) return; // boss bar already shows the time left

        List<LivingEntity> nearby = player.getWorld().getNearbyLivingEntities(player.getLocation(), PULSE_RANGE).stream()
                .filter(other -> Targets.isValidTarget(player, other))
                .toList();
        if (nearby.isEmpty()) {
            // Nothing to reveal, so the cooldown is not spent.
            player.sendActionBar(Component.text("Nothing nearby", NamedTextColor.GRAY));
            return;
        }

        for (LivingEntity other : nearby) highlight(other, PULSE_HIGHLIGHT_TICKS);

        cooldowns.start(player, PULSE_ID, "Hunter's Sight", BossBar.Color.YELLOW, PULSE_COOLDOWN_MS);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.2);
    }

    /** Glowing: an outline everyone can see, even through walls. Never cuts a longer glow short. */
    private void highlight(LivingEntity target, int ticks) {
        PotionEffect current = target.getPotionEffect(PotionEffectType.GLOWING);
        if (current != null && !current.isInfinite() && current.getDuration() >= ticks) return;
        // Particles off, icon on: a highlighted player can see why they are lit up.
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0, false, false, true));
    }
}
