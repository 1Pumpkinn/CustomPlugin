package net.rose.customPlugin.util;

import org.bukkit.GameMode;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * True damage helper for ability damage (players and mobs).
 *
 * <p>Ignores armor, enchantments (protection etc), the resistance effect, shield blocking and the hard hat.
 * It still goes through the normal vanilla damage pipeline ({@link EntityDamageEvent}), so the hurt
 * animation and sound play, totems still trigger, other plugins can still cancel it, and kill credit and
 * death messages work when an attacker is supplied.
 *
 * <p>Setup (once, in onEnable, before any effect that uses it): {@code new TrueDamage(this);}
 *
 * <p>Usage:
 * <pre>
 *   TrueDamage.apply(target, 4.0, attacker);
 *   TrueDamage.of(4.0).attacker(p).type(DamageType.LIGHTNING_BOLT).apply(target);
 * </pre>
 */
public final class TrueDamage implements Listener {

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private record Pending(double amount, boolean bypassAbsorption) {
    }

    /** Creates the instance and registers its listener (auto-unregistered when the plugin disables). */
    public TrueDamage(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static boolean apply(LivingEntity target, double amount, Entity attacker) {
        return of(amount).attacker(attacker).apply(target);
    }

    public static Builder of(double amount) {
        return new Builder(amount);
    }

    /** True while a true-damage hit is being processed on this entity. */
    public static boolean isTrueDamage(Entity entity) {
        return PENDING.containsKey(entity.getUniqueId());
    }

    public static final class Builder {
        private final double amount;
        private Entity attacker = null;
        private DamageType type = DamageType.GENERIC;
        private boolean ignoreIFrames = true;
        private boolean bypassAbsorption = false;

        private Builder(double amount) {
            this.amount = amount;
        }

        /** Source of the damage (knockback direction + kill credit). */
        public Builder attacker(Entity attacker) {
            this.attacker = attacker;
            return this;
        }

        public Builder type(DamageType type) {
            this.type = type;
            return this;
        }

        /** If true (default) the hit lands even during invulnerability frames. */
        public Builder ignoreIFrames(boolean ignore) {
            this.ignoreIFrames = ignore;
            return this;
        }

        /** If true, absorption hearts are skipped and the damage hits real health. Default false. */
        public Builder bypassAbsorption(boolean bypass) {
            this.bypassAbsorption = bypass;
            return this;
        }

        /** Applies the damage. Returns false if skipped (invalid, dead, creative, invulnerable...). */
        public boolean apply(LivingEntity target) {
            if (target == null || target.isDead() || !target.isValid()) return false;
            if (amount <= 0) return false;

            if (target instanceof Player player) {
                GameMode mode = player.getGameMode();
                if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return false;
            }
            if (target.isInvulnerable()) return false;

            UUID id = target.getUniqueId();
            if (PENDING.containsKey(id)) return false; // no re-entrancy

            if (ignoreIFrames) {
                target.setNoDamageTicks(0);
            }

            DamageSource.Builder source = DamageSource.builder(type);
            if (attacker != null) {
                source.withCausingEntity(attacker).withDirectEntity(attacker);
            }

            PENDING.put(id, new Pending(amount, bypassAbsorption));
            try {
                target.damage(amount, source.build());
            } finally {
                PENDING.remove(id);
            }
            return true;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onDamage(EntityDamageEvent event) {
        Pending pending = PENDING.get(event.getEntity().getUniqueId());
        if (pending == null) return;

        for (DamageModifier modifier : DamageModifier.values()) {
            if (modifier == DamageModifier.BASE) continue;
            if (!event.isApplicable(modifier)) continue;
            if (modifier == DamageModifier.ABSORPTION && !pending.bypassAbsorption()) continue;
            event.setDamage(modifier, 0);
        }
        event.setDamage(DamageModifier.BASE, pending.amount());
    }
}
