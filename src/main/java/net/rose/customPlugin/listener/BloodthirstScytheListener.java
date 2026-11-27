package net.rose.customPlugin.listener;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.effect.FrostEffects;
import net.rose.customPlugin.effect.SoulFreezeEffect;
import net.rose.customPlugin.item.BloodthirstScythe;
import net.rose.customPlugin.util.CooldownBars;
import net.rose.customPlugin.util.Targets;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bloodthirst Scythe: every melee hit stores a little health, offhand freezes nearby enemies, and
 * crouch + offhand spends everything stored to heal the wielder. Stored health is kept in HP (2 HP = 1 heart).
 */
public final class BloodthirstScytheListener implements Listener {

    // --- Passive: stored hearts ---
    private static final double HP_PER_HIT = 0.6;           // 0.3 hearts
    private static final double MAX_STORED_HP = 8.0;        // 4 hearts

    // --- Offhand: soul freeze ---
    private static final long FREEZE_COOLDOWN_MS = 30_000;
    private static final double FREEZE_RADIUS = 5.0;
    private static final int FREEZE_TICKS = 50;             // 2.5s

    private static final String FREEZE_ID = "bloodthirst_freeze";
    private static final String STORE_ID = "bloodthirst_store";

    private static final BlockData BLOOD_BLOCK = Material.REDSTONE_BLOCK.createBlockData();
    private static final Particle.DustOptions BLOOD_DUST = new Particle.DustOptions(Color.fromRGB(0xC4, 0x12, 0x1F), 1.2f);

    private final BloodthirstScythe scythe;
    private final CooldownBars cooldowns;
    private final FrostEffects frost;
    private final SoulFreezeEffect soulFreeze;

    private final Map<UUID, Double> stored = new HashMap<>();
    private final Set<UUID> barShown = new HashSet<>();

    public BloodthirstScytheListener(BloodthirstScythe scythe, CooldownBars cooldowns, FrostEffects frost,
                                     SoulFreezeEffect soulFreeze) {
        this.scythe = scythe;
        this.cooldowns = cooldowns;
        this.frost = frost;
        this.soulFreeze = soulFreeze;
    }

    // ------------------------------------------------------------------ Passive

    /** Melee hits with the scythe store health. Only real melee counts, so sweeps and ability damage don't. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!Targets.isValidTarget(attacker, event.getEntity())) return;
        if (!scythe.isBloodthirstScythe(attacker.getInventory().getItemInMainHand())) return;

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

    /** Offhand key (F) while holding the scythe: freeze, or heal when crouching. The swap itself is always cancelled. */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!scythe.isBloodthirstScythe(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);
        if (player.isSneaking()) {
            consumeHearts(player);
        } else {
            soulFreeze(player);
        }
    }

    private void soulFreeze(Player player) {
        if (cooldowns.isOnCooldown(player, FREEZE_ID)) return; // boss bar already shows the time left

        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(FREEZE_RADIUS, FREEZE_RADIUS, FREEZE_RADIUS)) {
            if (Targets.isValidTarget(player, entity)) targets.add((LivingEntity) entity);
        }
        if (targets.isEmpty()) return; // nothing to freeze, so no cooldown spent

        cooldowns.start(player, FREEZE_ID, "Soul Freeze", BossBar.Color.RED, FREEZE_COOLDOWN_MS);

        for (LivingEntity victim : targets) {
            frost.stun(victim, FREEZE_TICKS);
            soulFreeze.playFrozen(victim, FREEZE_TICKS);
        }
    }

    private void consumeHearts(Player player) {
        double hp = stored.getOrDefault(player.getUniqueId(), 0.0);
        if (hp <= 0) return;

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();
        if (player.getHealth() >= max) return; // already full: keep the hearts instead of wasting them

        stored.remove(player.getUniqueId());
        refreshBar(player);

        player.heal(hp, EntityRegainHealthEvent.RegainReason.CUSTOM);

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
        if (scythe.isBloodthirstScythe(player.getInventory().getItem(event.getNewSlot()))) {
            refreshBar(player);
        } else {
            hideBar(player);
        }
    }

    // ------------------------------------------------------------------ Lifecycle

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!scythe.isBloodthirstScythe(event.getItemDrop().getItemStack())) return;
        stored.remove(event.getPlayer().getUniqueId());
        hideBar(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        stored.remove(player.getUniqueId());
        hideBar(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        cooldowns.resume(player);
        if (scythe.isBloodthirstScythe(player.getInventory().getItemInMainHand())) {
            refreshBar(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        barShown.remove(player.getUniqueId()); // hideAll drops primed bars; stored hearts are kept
        cooldowns.hideAll(player);
    }
}
