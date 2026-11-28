package net.rose.customPlugin.listener.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.rose.customPlugin.item.Legendaries;
import net.rose.customPlugin.item.misc.KillToken;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Dropped CustomPlugin items (weapons, kill tokens) glow and show their name above them, so they are easy to
 * find on the ground. It's the real item entity that is changed: no display entities are spawned.
 *
 * <p>Dropped weapons also shoot a tall, blinking beam of particles into the sky, in the weapon's own color (red for
 * the Bloodthirst Scythe, yellow for the Zeus Disc...). Particles are sent with the "force" flag, so they show from
 * hundreds of blocks away instead of the ~32 blocks an item entity is tracked at.
 * See {@code dropped-item-beacon} in config.yml.
 */
public final class DroppedItemGlow implements Listener {

    private static final double BEAM_START_OFFSET = 0.5;

    private final String namespace;
    private final KillToken killToken;
    private final Legendaries legendaries;
    private final boolean beaconEnabled;
    private final boolean blink;
    private final double beaconHeight;
    private final double beaconSpacing;
    private final float particleSize;

    /**
     * Dropped weapons that currently get a beam, with the particle color of each. Entries are dropped once the item
     * is gone or its chunk unloads.
     */
    private final Map<Item, Particle.DustOptions> beacons = new HashMap<>();

    /** Whether the beam is drawn on the current pulse. Flips every pulse while blinking. */
    private boolean lit = true;

    public DroppedItemGlow(JavaPlugin plugin, KillToken killToken, Legendaries legendaries) {
        this.namespace = plugin.getName().toLowerCase(Locale.ROOT);
        this.killToken = killToken;
        this.legendaries = legendaries;
        this.beaconEnabled = plugin.getConfig().getBoolean("dropped-item-beacon.enabled", true);
        this.blink = plugin.getConfig().getBoolean("dropped-item-beacon.blink", true);
        this.beaconHeight = Math.max(1.0, plugin.getConfig().getDouble("dropped-item-beacon.height", 64.0));
        this.beaconSpacing = Math.max(0.5, plugin.getConfig().getDouble("dropped-item-beacon.spacing", 1.5));
        // Dust particles are capped at size 4 by the client.
        this.particleSize = (float) Math.max(0.5, Math.min(4.0, plugin.getConfig().getDouble("dropped-item-beacon.size", 2.5)));

        if (!beaconEnabled) return;
        // With blinking, every pulse switches the beam on or off, so this is half of one blink.
        long interval = Math.max(1L, plugin.getConfig().getLong("dropped-item-beacon.interval-ticks", 10L));
        for (World world : plugin.getServer().getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                trackIfWeapon(item);
            }
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulse, interval, interval);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        ItemStack stack = entity.getItemStack();
        if (!isPluginItem(stack)) return;

        entity.setGlowing(true);
        entity.customName(label(stack));
        entity.setCustomNameVisible(true);
        trackIfWeapon(entity);
    }

    /** Items that were already lying around when their chunk loaded (or the server started) get their beam back. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!beaconEnabled) return;
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Item item) trackIfWeapon(item);
        }
    }

    private void trackIfWeapon(Item item) {
        if (!beaconEnabled) return;
        ItemStack stack = item.getItemStack();
        if (!isPluginItem(stack) || killToken.isKillToken(stack)) return;

        Legendaries.Weapon weapon = legendaries.identify(stack);
        int rgb = weapon == null ? NamedTextColor.WHITE.value() : weapon.color().value();
        beacons.put(item, new Particle.DustOptions(Color.fromRGB(rgb), particleSize));
    }

    private void pulse() {
        lit = !blink || !lit;
        Iterator<Map.Entry<Item, Particle.DustOptions>> iterator = beacons.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Item, Particle.DustOptions> entry = iterator.next();
            Item item = entry.getKey();
            if (!item.isValid()) {
                iterator.remove();
                continue;
            }
            if (!lit) continue;

            Particle.DustOptions dust = entry.getValue();
            Location at = item.getLocation();
            World world = at.getWorld();
            // A fuller cluster around the item itself, then the beam going up from it.
            world.spawnParticle(Particle.DUST, at.getX(), at.getY() + 0.3, at.getZ(),
                    8, 0.3, 0.3, 0.3, 0.0, dust, true);
            for (double dy = BEAM_START_OFFSET; dy <= beaconHeight; dy += beaconSpacing) {
                world.spawnParticle(Particle.DUST, at.getX(), at.getY() + dy, at.getZ(),
                        1, 0.0, 0.0, 0.0, 0.0, dust, true);
            }
        }
    }

    /** Every item this plugin creates carries a persistent data tag under the plugin's namespace. */
    private boolean isPluginItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) return false;
        for (NamespacedKey key : stack.getItemMeta().getPersistentDataContainer().getKeys()) {
            if (key.getNamespace().equals(namespace)) return true;
        }
        return false;
    }

    private static Component label(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        Component name = meta.hasDisplayName()
                ? meta.displayName()
                : Component.translatable(stack.translationKey());
        if (stack.getAmount() > 1) {
            name = name.append(Component.text(" x" + stack.getAmount(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return name;
    }
}
