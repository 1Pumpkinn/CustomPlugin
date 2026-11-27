package net.rose.customPlugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom death messages for ability kills. Vanilla builds the message from the damage type, which for
 * our magic, drown and freeze damage ends up as a raw translation key like death.attack.magic.item.
 *
 * <p>Abilities call {@link #mark} right before they damage a player. If that player dies from damage
 * caused by the same attacker moments later, the vanilla message is replaced with one for the weapon.
 */
public final class DeathMessages implements Listener {

    public static final String TIDE_BLADE = "tide_blade";
    public static final String FROST_SPEAR = "frost_spear";
    public static final String VOLCANIC_AXE = "volcanic_axe";
    public static final String ZEUS_DISC = "zeus_disc";
    public static final String PETAL_BLADE = "petal_blade";
    public static final String TEMPEST_TRIDENT = "tempest_trident";
    public static final String HAT = "hat";
    public static final String BLOODTHIRST_SCYTHE = "bloodthirst_scythe";

    private static final long MARK_WINDOW_MS = 1_000;
    /** Above this many pending marks, the ones whose victim survived are swept out. */
    private static final int MARK_PRUNE_SIZE = 64;

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final String GENERIC = "<victim> was killed by <killer> using <weapon>";

    /** Death message keys that only show up when our ability damage names the attacker's held item. */
    private static final Set<String> RAW_KEYS = Set.of(
            "death.attack.magic.item",
            "death.attack.drown.item",
            "death.attack.freeze.item",
            "death.attack.lightningBolt.item",
            "death.attack.lightning_bolt.item");

    private record Mark(UUID killer, String weapon, long at) {
    }

    private record Style(String name, TextColor color, List<String> templates) {
    }

    private static final Map<String, Style> STYLES = Map.of(
            TIDE_BLADE, new Style("Tide Blade", NamedTextColor.AQUA, List.of(
                    "<victim> was dragged under by <killer>'s <weapon>",
                    "<killer> drowned <victim> with the <weapon>",
                    "<victim> forgot how to breathe thanks to <killer>")),
            FROST_SPEAR, new Style("Frost Spear", NamedTextColor.AQUA, List.of(
                    "<victim> was frozen solid by <killer>'s <weapon>",
                    "<killer> left <victim> out in the cold with the <weapon>",
                    "<victim> shattered into ice shards courtesy of <killer>")),
            VOLCANIC_AXE, new Style("Volcanic Axe", NamedTextColor.GOLD, List.of(
                    "<victim> was incinerated by <killer>'s <weapon>",
                    "<killer> erupted <victim> into ash with the <weapon>",
                    "<victim> got too close to <killer>'s volcano")),
            ZEUS_DISC, new Style("Zeus Disc", NamedTextColor.YELLOW, List.of(
                    "<victim> was struck down by <killer>'s <weapon>",
                    "<killer> smote <victim> with the <weapon>",
                    "<victim> was fried by <killer>'s lightning")),
            PETAL_BLADE, new Style("Petal Blade", TextColor.color(0xFF8FC8), List.of(
                    "<victim> was cut down by <killer>'s <weapon>",
                    "<killer> sent <victim> off in a flurry of petals",
                    "<victim> wilted to <killer>'s <weapon>")),
            TEMPEST_TRIDENT, new Style("Tempest Trident", TextColor.color(0x3FC8E0), List.of(
                    "<victim> was torn apart by <killer>'s <weapon>",
                    "<killer> trapped <victim> in a tempest with the <weapon>",
                    "<victim> was swept away by <killer>'s storm")),
            HAT, new Style("Hat", TextColor.color(0xC03030), List.of(
                    "<victim> was shredded by <killer>'s <weapon>",
                    "<killer> sliced <victim> up with a spinning <weapon>",
                    "<victim> lost a fight with <killer>'s <weapon>")),
            BLOODTHIRST_SCYTHE, new Style("Bloodthirst Scythe", TextColor.color(0xC4121F), List.of(
                    "<victim> was drained dry by <killer>'s <weapon>",
                    "<killer> swept <victim> away with the <weapon>",
                    "<victim> fed <killer>'s <weapon>")));

    private final Map<UUID, Mark> marks = new HashMap<>();

    /** Call right before dealing ability damage to {@code victim}. Only players have death messages, so mobs are ignored. */
    public void mark(LivingEntity victim, Player killer, String weapon) {
        if (!(victim instanceof Player)) return;
        long now = System.currentTimeMillis();
        if (marks.size() > MARK_PRUNE_SIZE) {
            marks.values().removeIf(mark -> now - mark.at() > MARK_WINDOW_MS);
        }
        marks.put(victim.getUniqueId(), new Mark(killer.getUniqueId(), weapon, now));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Mark mark = marks.remove(victim.getUniqueId());
        Entity cause = event.getDamageSource().getCausingEntity();

        if (mark != null
                && System.currentTimeMillis() - mark.at() <= MARK_WINDOW_MS
                && cause != null
                && cause.getUniqueId().equals(mark.killer())
                && Bukkit.getPlayer(mark.killer()) != null) {
            event.deathMessage(forWeapon(victim, Bukkit.getPlayer(mark.killer()), mark.weapon()));
            return;
        }

        if (event.deathMessage() instanceof TranslatableComponent translatable
                && RAW_KEYS.contains(translatable.key())
                && cause instanceof Player killer) {
            event.deathMessage(MINI.deserialize(GENERIC,
                    Placeholder.component("victim", victim.name()),
                    Placeholder.component("killer", killer.name()),
                    Placeholder.component("weapon", killer.getInventory().getItemInMainHand().effectiveName())));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        marks.remove(event.getPlayer().getUniqueId());
    }

    private static Component forWeapon(Player victim, Player killer, String weapon) {
        Style style = STYLES.get(weapon);
        String template = GENERIC;
        Component weaponName = Component.text(weapon);
        if (style != null) {
            template = style.templates().get(ThreadLocalRandom.current().nextInt(style.templates().size()));
            weaponName = Component.text(style.name(), style.color());
        }
        return MINI.deserialize(template,
                Placeholder.component("victim", victim.name()),
                Placeholder.component("killer", killer.name()),
                Placeholder.component("weapon", weaponName));
    }
}
