package net.rose.customPlugin.item.bow;

import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.api.Ability;
import net.rose.customPlugin.api.CustomWeapon;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.listener.bow.TricksterBowListener;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class TricksterBow extends CustomWeapon {

    public TricksterBow(JavaPlugin plugin) {
        super(plugin, "tricksterbow", "trickster_bow", "Trickster Bow", NamedTextColor.LIGHT_PURPLE, Material.BOW);
    }

    @Override
    public List<Ability> abilities() {
        return List.of(
                Ability.offhand("Prime your next shot to fire 3 stronger arrows that bounce between players and mobs"),
                Ability.passive("Shots have a 10% chance to swap the hit player's held item with another hotbar slot"));
    }

    @Override
    public Listener createListener(WeaponContext context) {
        return new TricksterBowListener(context, this);
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
