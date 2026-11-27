package net.rose.customPlugin.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class BloodthirstScythe {

    public static final TextColor BLOOD = TextColor.color(0xC4121F);

    private final JavaPlugin plugin;
    private final NamespacedKey key;

    public BloodthirstScythe(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "bloodthirst_scythe");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Bloodthirst Scythe", BLOOD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                ability("Hit", "Store 0.3 hearts per hit (max 4)"),
                ability("Offhand", "Freeze nearby enemies in place"),
                ability("Crouch + Offhand", "Spend every stored heart to heal yourself")
        ));
        // Setting any attribute modifier replaces the item's defaults, so damage and speed are both restated.
        // 7 damage at 1.6 attacks/s, the same as a netherite sword.
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                new NamespacedKey(plugin, "bloodthirst_scythe_damage"), 6.0,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                new NamespacedKey(plugin, "bloodthirst_scythe_speed"), -2.4,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.setItemModel(key); // texture pack model: customplugin:<name>
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBloodthirstScythe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static Component ability(String trigger, String description) {
        return Component.text(trigger, BLOOD)
                .append(Component.text(" - " + description, NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }
}
