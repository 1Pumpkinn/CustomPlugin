package net.rose.customPlugin.item.sword;

import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.api.Ability;
import net.rose.customPlugin.api.CustomWeapon;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.listener.sword.TideBladeListener;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class TideBlade extends CustomWeapon {

    public TideBlade(JavaPlugin plugin) {
        super(plugin, "tideblade", "tide_blade", "Tide Blade", NamedTextColor.AQUA, Material.NETHERITE_SWORD);
    }

    @Override
    public List<Ability> abilities() {
        return List.of(Ability.offhand(
                "Drown the next player or mob you hit for 8s: their air runs out, then they take 3.75 hearts of true damage"));
    }

    @Override
    public List<String> deathMessages() {
        return List.of(
                "<victim> was dragged under by <killer>'s <weapon>",
                "<killer> drowned <victim> with the <weapon>",
                "<victim> forgot how to breathe thanks to <killer>");
    }

    @Override
    public Listener createListener(WeaponContext context) {
        return new TideBladeListener(context, this);
    }

    @Override
    public int defaultTier() {
        return 3;
    }

    @Override
    public int defaultPrice() {
        return 8;
    }
}
