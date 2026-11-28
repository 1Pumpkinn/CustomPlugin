package net.rose.customPlugin.item.scythe;

import net.kyori.adventure.text.format.TextColor;
import net.rose.customPlugin.api.Ability;
import net.rose.customPlugin.api.CustomWeapon;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.effect.VampireSweepEffect;
import net.rose.customPlugin.listener.scythe.BloodthirstScytheListener;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class BloodthirstScythe extends CustomWeapon {

    public static final TextColor BLOOD = TextColor.color(0xC4121F);

    public BloodthirstScythe(JavaPlugin plugin) {
        super(plugin, "bloodthirstscythe", "bloodthirst_scythe", "Bloodthirst Scythe", BLOOD, Material.NETHERITE_SWORD);
    }

    @Override
    public List<Ability> abilities() {
        return List.of(
                Ability.of("Hit", "Store 0.3 hearts per hit (max 4)"),
                Ability.offhand("Vampire sweep: slash the arc in front of you for 2.5 hearts of true damage and steal it back as health"),
                Ability.of("Crouch + Offhand", "Spend every stored heart to heal yourself (30s cooldown, no blood is stored while it recharges)"));
    }

    @Override
    public Listener createListener(WeaponContext context) {
        return new BloodthirstScytheListener(context, this, new VampireSweepEffect(context.deaths()));
    }

    @Override
    public int defaultTier() {
        return 1;
    }

    @Override
    public int defaultPrice() {
        return 72;
    }
}
