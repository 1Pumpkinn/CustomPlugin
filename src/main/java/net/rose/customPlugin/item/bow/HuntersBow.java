package net.rose.customPlugin.item.bow;

import net.kyori.adventure.text.format.TextColor;
import net.rose.customPlugin.api.Ability;
import net.rose.customPlugin.api.CustomWeapon;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.listener.bow.HuntersBowListener;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Reference implementation of the weapon API: the item is described here, the abilities live in the listener. */
public final class HuntersBow extends CustomWeapon {

    public static final TextColor COLOR = TextColor.color(0xE8890C);

    public HuntersBow(JavaPlugin plugin) {
        super(plugin, "huntersbow", "hunters_bow", "Hunter's Bow", COLOR, Material.BOW);
    }

    @Override
    public List<Ability> abilities() {
        return List.of(
                Ability.offhand("Highlight every player and mob near you, so they can be seen through walls"),
                Ability.passive("Players and mobs hit by your arrows are highlighted"));
    }

    @Override
    public Listener createListener(WeaponContext context) {
        return new HuntersBowListener(context, this);
    }
}
