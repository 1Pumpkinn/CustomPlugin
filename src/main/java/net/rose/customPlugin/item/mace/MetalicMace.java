package net.rose.customPlugin.item.mace;

import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.api.Ability;
import net.rose.customPlugin.api.CustomWeapon;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.effect.ChainEffects;
import net.rose.customPlugin.listener.mace.MetalicMaceListener;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class MetalicMace extends CustomWeapon {

    private final JavaPlugin plugin;
    private ChainEffects chains;

    public MetalicMace(JavaPlugin plugin) {
        super(plugin, "metalicmace", "metalic_mace", "Metalic Mace", NamedTextColor.GRAY, Material.MACE);
        this.plugin = plugin;
    }

    @Override
    public List<Ability> abilities() {
        return List.of(
                Ability.offhand("Pull a player or mob in with a chain, or look at a block to chain yourself to it and pull yourself there"),
                Ability.of("Chain", "The pulled target is slowed for 8s and can't jump or use wind charges"));
    }

    @Override
    public Listener createListener(WeaponContext context) {
        this.chains = new ChainEffects(plugin);
        return new MetalicMaceListener(context, this, chains);
    }

    @Override
    public void onDisable() {
        if (chains != null) chains.shutdown();
    }

    @Override
    public int defaultTier() {
        return 1;
    }

    @Override
    public int defaultPrice() {
        return 80;
    }
}
