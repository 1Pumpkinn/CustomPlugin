package net.rose.customPlugin.item.axe;

import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.api.Ability;
import net.rose.customPlugin.api.CustomWeapon;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.effect.AxeSpinEffect;
import net.rose.customPlugin.effect.EruptionEffect;
import net.rose.customPlugin.listener.axe.VolcanicAxeListener;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class VolcanicAxe extends CustomWeapon {

    private final JavaPlugin plugin;
    private EruptionEffect eruption;
    private AxeSpinEffect spin;

    public VolcanicAxe(JavaPlugin plugin) {
        super(plugin, "volcanicaxe", "volcanic_axe", "Volcanic Axe", NamedTextColor.GOLD, Material.NETHERITE_AXE);
        this.plugin = plugin;
    }

    @Override
    public List<Ability> abilities() {
        return List.of(
                Ability.offhand("Look at a player or mob to erupt the ground under them"),
                Ability.of("Crouch + Offhand", "Spin the axe around you, burning anyone it hits"));
    }

    @Override
    public Listener createListener(WeaponContext context) {
        this.eruption = new EruptionEffect(plugin, context.deaths());
        this.spin = new AxeSpinEffect(plugin, context.deaths());
        return new VolcanicAxeListener(context, this, eruption, spin);
    }

    @Override
    protected void customize(ItemMeta meta) {
        // Barely slower than a vanilla netherite axe (1.0 -> 0.95 attacks/s). Setting any attribute modifier
        // replaces the item's defaults, so the netherite axe's damage (10) is restated too.
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                new NamespacedKey(plugin, "volcanic_axe_damage"), 9.0,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                new NamespacedKey(plugin, "volcanic_axe_speed"), -3.05,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
    }

    @Override
    public void onDisable() {
        if (eruption != null) eruption.removeAll();
        if (spin != null) spin.removeAll();
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
