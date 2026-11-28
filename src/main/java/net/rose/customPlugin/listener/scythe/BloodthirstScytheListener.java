package net.rose.customPlugin.listener.scythe;

import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.api.AbilityListener;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.effect.VampireSweepEffect;
import net.rose.customPlugin.item.scythe.BloodthirstScythe;
import net.rose.customPlugin.util.Targets;
import net.rose.customPlugin.util.TrueDamage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bloodthirst Scythe: every melee hit stores a little health, offhand is a vampire sweep that steals health, and
 * crouch + offhand spends everything stored to heal the wielder. Stored health is kept in HP (2 HP = 1 heart).
 */
public final class BloodthirstScytheListener extends AbilityListener<BloodthirstScythe> {

    // --- Passive: stored hearts ---
    private static final double HP_PER_HIT = 0.6;           // 0.3 hearts
    private static final double MAX_STORED_HP = 8.0;        // 4 hearts

    // --- Offhand: vampire sweep ---
    private static final long SWEEP_COOLDOWN_MS = 30_000;

    // --- Crouch + offhand: spend the stored hearts ---
    private static final long HEAL_COOLDOWN_MS = 30_000;

    private static final String SWEEP_ID = "bloodthirst_sweep";
    private static final String HEAL_ID = "bloodthirst_heal";
    private static final String STORE_ID = "bloodthirst_store";

    private static final BlockData BLOOD_BLOCK = Material.REDSTONE_BLOCK.createBlockData();
    private static final Particle.DustOptions BLOOD_DUST = new Particle.DustOptions(Color.fromRGB(0xC4, 0x12, 0x1F), 1.2f);

    private final VampireSweepEffect sweep;

    private final Map<UUID, Double> stored = new HashMap<>();
    private final Set<UUID> barShown = new HashSet<>();

    public BloodthirstScytheListener(WeaponContext context, BloodthirstScythe scythe, VampireSweepEffect sweep) {
        super(context, scythe);
        this.sweep = sweep;
    }

    // ------------------------------------------------------------------ Passive

    /** Melee hits with the scythe store health, unless the heal is on cooldown. Only real melee counts, so sweeps and ability damage don't. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (TrueDamage.isTrueDamage(event.getEntity())) return; // ability damage is not a melee hit
        if (!Targets.isValidTarget(attacker, event.getEntity())) return;
        if (!weapon.isWeapon(attacker.getInventory().getItemInMainHand())) return;
        if (cooldowns.isOnCooldown(attacker, HEAL_ID)) return; // no new blood while the heal is on cooldown

        LivingEntity victim = (LivingEntity) event.getEntity();
        victim.getWorld().spawnParticle(Particle.BLOCK, victim.getLocation().add(0, 1.0, 0),
                12, 0.25, 0.4, 0.25, 0.0, BLOOD_BLOCK);

        double before = stored.getOrDefault(attacker.getUniqueId(), 0.0);
        double after = Math.min(MAX_STORED_HP, before + HP_PER_HIT);
        if (after == before) return; // already full
        stored.put(attacker.getUniqueId(), after);
        refreshBar(attacker);
    }

    // ------------------------------------------------------------------ Offhand

    /** Offhand key (F) while holding the scythe: vampire sweep, or heal when crouching. The swap itself is always cancelled. */
    @Override
    protected void onOffhand(Player player, boolean sneaking) {
        if (sneaking) {
            consumeHearts(player);
        } else {
            vampireSweep(player);
        }
    }

    private void vampireSweep(Player player) {
        if (cooldowns.isOnCooldown(player, SWEEP_ID)) return; // boss bar already shows the time left
        if (!sweep.cast(player)) return; // nobody in the arc, so no cooldown spent

        cooldowns.start(player, SWEEP_ID, "Vampire Sweep", BossBar.Color.RED, SWEEP_COOLDOWN_MS);
    }

    private void consumeHearts(Player player) {
        if (cooldowns.isOnCooldown(player, HEAL_ID)) return;
        double hp = stored.getOrDefault(player.getUniqueId(), 0.0);
        if (hp <= 0) return;

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();
        if (player.getHealth() >= max) return; // already full: keep the hearts instead of wasting them

        stored.remove(player.getUniqueId());
        refreshBar(player);

        player.heal(hp, EntityRegainHealthEvent.RegainReason.CUSTOM);
        cooldowns.start(player, HEAL_ID, "Blood Spend", BossBar.Color.RED, HEAL_COOLDOWN_MS);

        Location at = player.getLocation().add(0, 1.0, 0);
        player.getWorld().spawnParticle(Particle.HEART, at, 8, 0.4, 0.5, 0.4, 0.0);
        player.getWorld().spawnParticle(Particle.DUST, at, 25, 0.4, 0.6, 0.4, 0.0, BLOOD_DUST);
        player.getWorld().playSound(at, Sound.ENTITY_GENERIC_DRINK, 1f, 0.7f);
        player.getWorld().playSound(at, Sound.BLOCK_SOUL_SAND_BREAK, 0.8f, 1.4f);
    }

    // ------------------------------------------------------------------ Boss bar

    /** Shows the stored hearts as a primed boss bar, or hides it when nothing is stored. */
    private void refreshBar(Player player) {
        UUID id = player.getUniqueId();
        double hp = stored.getOrDefault(id, 0.0);
        if (hp <= 0) {
            hideBar(player);
            return;
        }
        if (barShown.add(id)) {
            cooldowns.prime(player, STORE_ID, "Bloodthirst", BossBar.Color.RED);
        }
        cooldowns.updatePrimed(player, STORE_ID,
                Component.text(String.format("Bloodthirst - %.1f/%.0f hearts", hp / 2.0, MAX_STORED_HP / 2.0),
                        NamedTextColor.WHITE),
                (float) (hp / MAX_STORED_HP));
    }

    private void hideBar(Player player) {
        if (barShown.remove(player.getUniqueId())) {
            cooldowns.unprime(player, STORE_ID);
        }
    }

    /** The bar only shows while the scythe is in hand. */
    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (weapon.isWeapon(player.getInventory().getItem(event.getNewSlot()))) {
            refreshBar(player);
        } else {
            hideBar(player);
        }
    }

    // ------------------------------------------------------------------ Lifecycle

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!weapon.isWeapon(event.getItemDrop().getItemStack())) return;
        stored.remove(event.getPlayer().getUniqueId());
        hideBar(event.getPlayer());
    }

    @Override
    protected void onPlayerDeath(Player player) {
        stored.remove(player.getUniqueId());
        hideBar(player);
    }

    @Override
    protected void onPlayerJoin(Player player) {
        if (weapon.isWeapon(player.getInventory().getItemInMainHand())) {
            refreshBar(player);
        }
    }

    @Override
    protected void onPlayerQuit(Player player) {
        barShown.remove(player.getUniqueId()); // hideAll drops primed bars; stored hearts are kept
    }
}
