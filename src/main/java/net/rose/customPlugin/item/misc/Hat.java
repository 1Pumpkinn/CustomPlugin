package net.rose.customPlugin.item.misc;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.format.TextColor;
import net.rose.customPlugin.api.Ability;
import net.rose.customPlugin.api.CustomWeapon;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.effect.HatEffects;
import net.rose.customPlugin.listener.disc.HatListener;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class Hat extends CustomWeapon {

    public static final TextColor COLOR = TextColor.color(0xC03030);

    private final JavaPlugin plugin;
    private HatEffects effects;
    private HatListener listener;

    public Hat(JavaPlugin plugin) {
        // A music disc is a flat round sprite, like the Zeus Disc; the pack model replaces how it looks.
        super(plugin, "hat", "hat", "Old Man's Hat", COLOR, Material.MUSIC_DISC_CAT);
        this.plugin = plugin;
    }

    @Override
    public List<Ability> abilities() {
        return List.of(
                Ability.of("Right-click / Offhand", "Throw the hat: it bounces anyone who touches it for a few seconds (thrown far, it hangs in the air as a jump pad)"),
                Ability.of("Crouch + Right-click / Offhand", "Throw the hat: if it hits someone it sticks to them and spins like a razor"));
    }

    @Override
    public List<String> deathMessages() {
        return List.of(
                "<victim> was shredded by <killer>'s <weapon>",
                "<killer> sliced <victim> up with a spinning <weapon>",
                "<victim> lost a fight with <killer>'s <weapon>");
    }

    @Override
    public Listener createListener(WeaponContext context) {
        this.effects = new HatEffects(plugin, this, context.deaths());
        this.listener = new HatListener(context, this, effects);
        return listener;
    }

    @Override
    protected boolean maxEnchanted() {
        return false; // the hat carries no enchantments
    }

    @Override
    protected void customize(ItemMeta meta) {
        meta.setMaxStackSize(1);
    }

    @Override
    protected void finish(ItemStack item) {
        // No jukebox use, and no music disc line in the tooltip.
        item.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE);
    }

    /** Ends every flight (each hat goes back to its thrower) and every hat that is out. */
    @Override
    public void onDisable() {
        if (listener != null) listener.shutdown();
        if (effects != null) effects.removeAll();
    }

    @Override
    public int defaultTier() {
        return 2;
    }

    @Override
    public int defaultPrice() {
        return 32;
    }
}
