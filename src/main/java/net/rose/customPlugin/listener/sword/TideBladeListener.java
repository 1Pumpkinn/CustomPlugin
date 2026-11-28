package net.rose.customPlugin.listener.sword;

import net.rose.customPlugin.api.PrimedAbility;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.api.AbilityListener;
import net.kyori.adventure.bossbar.BossBar;
import net.rose.customPlugin.item.sword.TideBlade;
import net.rose.customPlugin.util.DeathMessages;
import net.rose.customPlugin.util.Targets;
import net.rose.customPlugin.util.TrueDamage;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TideBladeListener extends AbilityListener<TideBlade> {

    // --- Drown ---
    private static final int DROWN_DURATION_TICKS = 160;     // 8s total
    private static final int AIR_DRAIN_TICKS = 60;           // air bar empties over the first 3s
    private static final int DROWN_DAMAGE_INTERVAL = 20;     // then damage once per second
    private static final double DROWN_DAMAGE = 1.5;          // 5 ticks of damage = 7.5 HP total
    private static final long DROWN_COOLDOWN_MS = 30_000;

    private final DeathMessages deaths;
    private final PrimedAbility drown;

    private final Map<UUID, BukkitTask> drowning = new HashMap<>();

    public TideBladeListener(WeaponContext context, TideBlade tideBlade) {
        super(context, tideBlade);
        this.deaths = context.deaths();
        this.drown = primedAbility("drown", "Drown", BossBar.Color.BLUE, DROWN_COOLDOWN_MS);
    }

    /** Offhand key (F) while holding the Tide Blade: arms Drown. */
    @Override
    protected void onOffhand(Player player, boolean sneaking) {
        if (drown.prime(player)) player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1f, 0.8f);
    }

    /** The next target (player or mob) hit with the Tide Blade while armed starts drowning. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!Targets.isValidTarget(attacker, event.getEntity())) return;
        LivingEntity victim = (LivingEntity) event.getEntity();
        if (!drown.isPrimed(attacker)) return;
        if (!weapon.isWeapon(attacker.getInventory().getItemInMainHand())) return;

        drown.spend(attacker);
        startDrowning(attacker, victim);
    }

    @Override
    protected void onPlayerQuit(Player player) {
        stopDrowning(player);
    }

    // ------------------------------------------------------------------ Drown

    private void startDrowning(Player attacker, LivingEntity victim) {
        stopDrowning(victim);

        BukkitTask task = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (Targets.isGone(victim) || ticks >= DROWN_DURATION_TICKS) {
                    stopDrowning(victim);
                    return;
                }

                // Air bar drains to empty, then stays empty
                int max = victim.getMaximumAir();
                victim.setRemainingAir(Math.max(0, max - (max * ticks / AIR_DRAIN_TICKS)));

                if (ticks >= AIR_DRAIN_TICKS && (ticks - AIR_DRAIN_TICKS) % DROWN_DAMAGE_INTERVAL == 0) {
                    // Drowning damage credited to the attacker, so kills count as theirs.
                    // The DROWN damage type already plays the drowning hurt sound itself.
                    deaths.mark(victim, attacker, DeathMessages.TIDE_BLADE);
                    TrueDamage.of(DROWN_DAMAGE).attacker(attacker).type(DamageType.DROWN).apply(victim);
                }

                if (ticks % 4 == 0) {
                    victim.getWorld().spawnParticle(Particle.BUBBLE,
                            victim.getEyeLocation(), 4, 0.25, 0.15, 0.25, 0.02);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        drowning.put(victim.getUniqueId(), task);
    }

    private void stopDrowning(LivingEntity victim) {
        BukkitTask task = drowning.remove(victim.getUniqueId());
        if (task == null) return;
        task.cancel();
        if (!Targets.isGone(victim)) {
            victim.setRemainingAir(victim.getMaximumAir());
        }
    }
}
