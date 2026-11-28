package net.rose.customPlugin.item.trident;

import net.kyori.adventure.text.format.TextColor;
import net.rose.customPlugin.api.Ability;
import net.rose.customPlugin.api.CustomWeapon;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.effect.TempestSpinEffect;
import net.rose.customPlugin.listener.trident.TempestTridentListener;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class TempestTrident extends CustomWeapon {

    public static final TextColor COLOR = TextColor.color(0x3FC8E0);

    private final JavaPlugin plugin;
    private TempestSpinEffect spin;

    public TempestTrident(JavaPlugin plugin) {
        super(plugin, "tempesttrident", "tempest_trident", "Tempest Trident", COLOR, Material.TRIDENT);
        this.plugin = plugin;
    }

    @Override
    public List<Ability> abilities() {
        return List.of(
                Ability.offhand("Prime your next throw"),
                Ability.of("Throw", "The trident spins around the player it hits for 3s, dealing 4 hearts of true damage"));
    }

    @Override
    public Listener createListener(WeaponContext context) {
        this.spin = new TempestSpinEffect(plugin, context.deaths());
        return new TempestTridentListener(context, this, spin);
    }

    @Override
    public void onDisable() {
        if (spin != null) spin.removeAll();
    }

    @Override
    public int defaultTier() {
        return 2;
    }

    @Override
    public int defaultPrice() {
        return 24;
    }
}
