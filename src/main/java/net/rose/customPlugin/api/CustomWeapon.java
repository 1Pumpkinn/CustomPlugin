package net.rose.customPlugin.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.rose.customPlugin.util.MaxEnchants;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;

/**
 * Base class for a custom weapon. Describe the weapon here and {@link WeaponManager#register} does the rest:
 * the /weapon command, the shop, the weapon limit, death messages for carried weapons, the dropped-item beacon,
 * lore refreshing, and the {@code weapons.<id>} entry in config.yml.
 *
 * <pre>
 * public final class MyBow extends CustomWeapon {
 *     public MyBow(JavaPlugin plugin) {
 *         super(plugin, "mybow", "my_bow", "My Bow", NamedTextColor.GREEN, Material.BOW);
 *     }
 *     public List&lt;Ability&gt; abilities() { return List.of(Ability.offhand("Does a thing")); }
 *     public Listener createListener(WeaponContext ctx) { return new MyBowListener(ctx, this); }
 * }
 * // in CustomPlugin.onEnable:  weapons.register(new MyBow(this));
 * </pre>
 *
 * See docs/ADDING_A_WEAPON.md for the full checklist (including the resource pack files).
 */
public abstract class CustomWeapon {

    private final String id;
    private final String displayName;
    private final TextColor color;
    private final Material material;
    private final NamespacedKey key;

    /**
     * @param id          the /weapon name and the key under {@code weapons:} in config.yml, lower case, no spaces
     * @param keyName     the item's persistent-data tag AND its resource pack model name
     *                    ({@code customplugin:<keyName>}, i.e. assets/customplugin/items/&lt;keyName&gt;.json)
     * @param displayName the name shown on the item and in messages
     * @param color       the weapon's signature color
     * @param material    the vanilla item it is built on (it decides how the item behaves: bow, sword, ...)
     */
    protected CustomWeapon(JavaPlugin plugin, String id, String keyName, String displayName, TextColor color,
                           Material material) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.material = material;
        this.key = new NamespacedKey(plugin, keyName);
    }

    /** The lore lines, in order. */
    public abstract List<Ability> abilities();

    /**
     * The listener that implements the abilities, or null if the weapon has none (a plain stat stick).
     * Called once, when the weapon is registered. Extend {@link AbilityListener} to skip the boilerplate.
     */
    public Listener createListener(WeaponContext context) {
        return null;
    }

    /** Tier written to config.yml the first time the weapon is registered (the server owner can change it afterwards). */
    public int defaultTier() {
        return 2;
    }

    /** Shop price in kill tokens written to config.yml the first time the weapon is registered. */
    public int defaultPrice() {
        return 32;
    }

    /**
     * Death messages for players killed by this weapon's abilities, one picked at random. They use the placeholders
     * {@code <victim>}, {@code <killer>} and {@code <weapon>}. Abilities call
     * {@code deaths.mark(victim, killer, weapon.key().getKey())} right before they deal damage. Empty (the default)
     * means the generic "killed by X using Y" message.
     */
    public List<String> deathMessages() {
        return List.of();
    }

    /** Whether new copies come fully enchanted, like every other weapon. */
    protected boolean maxEnchanted() {
        return true;
    }

    /** Enchantments (by key, e.g. {@code "fire_aspect"}) the automatic max enchants leave off this weapon. */
    protected Set<String> skippedEnchants() {
        return Set.of();
    }

    /** Last chance to change the item's meta (attributes, flags, unbreakable...) before it is finished. */
    protected void customize(ItemMeta meta) {
    }

    /** Runs once the item is complete: for what the meta can't reach (data components such as the jukebox one). */
    protected void finish(ItemStack item) {
    }

    /** Called from the plugin's onDisable: end anything the weapon has running (displays, tasks, state). */
    public void onDisable() {
    }

    // ------------------------------------------------------------------ Item

    /** A fresh copy of the weapon. */
    public final ItemStack create() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(displayName, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(abilities().stream().map(ability -> ability.toLore(color)).toList());
        meta.setItemModel(key); // resource pack model: customplugin:<keyName>
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        customize(meta);
        item.setItemMeta(meta);
        if (maxEnchanted()) MaxEnchants.apply(item, skippedEnchants()); // weapons always come fully enchanted
        finish(item);
        return item;
    }

    /**
     * Whether the stack is this weapon. Safe to call with null or air. The type is checked first because some
     * weapons are asked about every tick for every online player, and getItemMeta() copies the meta.
     */
    public final boolean isWeapon(ItemStack item) {
        if (item == null || item.getType() != material || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    // --------------------------------------------------------------- Identity

    public final String id() {
        return id;
    }

    public final String displayName() {
        return displayName;
    }

    public final TextColor color() {
        return color;
    }

    public final NamespacedKey key() {
        return key;
    }
}
