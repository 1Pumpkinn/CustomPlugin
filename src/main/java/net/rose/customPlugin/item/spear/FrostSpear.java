package net.rose.customPlugin.item.spear;

import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.api.Ability;
import net.rose.customPlugin.api.CustomWeapon;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.effect.FrostEffects;
import net.rose.customPlugin.listener.spear.FrostSpearListener;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;

public final class FrostSpear extends CustomWeapon {

    private final FrostEffects frost;

    /** @param frost shared with the stun listener, which is why the plugin creates it and shuts it down */
    public FrostSpear(JavaPlugin plugin, FrostEffects frost) {
        super(plugin, "frostspear", "frost_spear", "Frost Spear", NamedTextColor.AQUA, spearMaterial());
        this.frost = frost;
    }

    @Override
    public List<Ability> abilities() {
        return List.of(
                Ability.offhand("Dash forward, freezing and stunning every player and mob you dash into"),
                Ability.passive("Stabbing a player or mob frosts and slows them"));
    }

    @Override
    public Listener createListener(WeaponContext context) {
        return new FrostSpearListener(context, this, frost);
    }

    @Override
    protected Set<String> skippedEnchants() {
        return Set.of("fire_aspect"); // fully enchanted, but a frost spear never burns
    }

    @Override
    public int defaultTier() {
        return 1;
    }

    @Override
    public int defaultPrice() {
        return 48;
    }

    /** Netherite spear (1.21.11+), falling back to a trident on versions without spears. */
    private static Material spearMaterial() {
        Material spear = Material.matchMaterial("NETHERITE_SPEAR");
        return spear != null ? spear : Material.TRIDENT;
    }
}
