package net.rose.customPlugin.item.sword;

import net.kyori.adventure.text.format.TextColor;
import net.rose.customPlugin.api.Ability;
import net.rose.customPlugin.api.CustomWeapon;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.effect.PetalEffect;
import net.rose.customPlugin.listener.sword.PetalBladeListener;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class PetalBlade extends CustomWeapon {

    public static final TextColor PINK = TextColor.color(0xFF8FC8);

    private final JavaPlugin plugin;
    private PetalEffect petals;

    public PetalBlade(JavaPlugin plugin) {
        super(plugin, "petalblade", "petal_blade", "Petal Blade", PINK, Material.NETHERITE_SWORD);
        this.plugin = plugin;
    }

    @Override
    public List<Ability> abilities() {
        return List.of(
                Ability.offhand("Summon 3 petals that circle you"),
                Ability.of("Right-click", "Throw a petal for 1 heart of damage"));
    }

    @Override
    public Listener createListener(WeaponContext context) {
        this.petals = new PetalEffect(plugin, context.deaths());
        return new PetalBladeListener(context, this, petals);
    }

    @Override
    public void onDisable() {
        if (petals != null) petals.removeAll();
    }

    @Override
    public int defaultTier() {
        return 3;
    }

    @Override
    public int defaultPrice() {
        return 16;
    }
}
