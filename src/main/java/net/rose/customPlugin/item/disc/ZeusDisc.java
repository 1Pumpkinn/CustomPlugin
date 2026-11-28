package net.rose.customPlugin.item.disc;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.api.Ability;
import net.rose.customPlugin.api.CustomWeapon;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.effect.ZeusLightning;
import net.rose.customPlugin.listener.disc.ZeusDiscListener;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ZeusDisc extends CustomWeapon {

    private final JavaPlugin plugin;
    private ZeusDiscListener listener;

    public ZeusDisc(JavaPlugin plugin) {
        // A music disc is a flat round sprite, so it reads as a disc while it spins (no resource pack needed).
        super(plugin, "zeusdisc", "zeus_disc", "Zeus Disc", NamedTextColor.YELLOW, Material.MUSIC_DISC_13);
        this.plugin = plugin;
    }

    @Override
    public List<Ability> abilities() {
        return List.of(
                Ability.of("Right-click", "Throw the disc. It ricochets off walls and flies back to you"),
                Ability.offhand("Prime your next throw to strike everything it hits with lightning"),
                Ability.passive("Hitting something near another target arcs sparks to nearby players and mobs for half a heart"));
    }

    @Override
    public List<String> deathMessages() {
        return List.of(
                "<victim> was struck down by <killer>'s <weapon>",
                "<killer> smote <victim> with the <weapon>",
                "<victim> was fried by <killer>'s lightning");
    }

    @Override
    public Listener createListener(WeaponContext context) {
        this.listener = new ZeusDiscListener(context, this, new ZeusLightning(plugin, context.deaths()));
        return listener;
    }

    @Override
    protected boolean maxEnchanted() {
        return false; // the disc carries no enchantments
    }

    @Override
    protected void customize(ItemMeta meta) {
        meta.setEnchantmentGlintOverride(false); // no enchant glint on any weapon
    }

    @Override
    protected void finish(ItemStack item) {
        // No jukebox use, and no "C418 - 13" line in the tooltip.
        item.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE);
    }

    /** Ends every disc in the air (each goes back to its thrower). */
    @Override
    public void onDisable() {
        if (listener != null) listener.shutdown();
    }

    @Override
    public int defaultTier() {
        return 2;
    }

    @Override
    public int defaultPrice() {
        return 48;
    }
}
