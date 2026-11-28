package net.rose.customPlugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
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
 * caused by the same attacker moments later, the vanilla message is replaced with one for the weapon. Each weapon
 * declares its messages itself ({@code CustomWeapon.deathMessages()}); see {@link #register}.
 */
public final class DeathMessages implements Listener {

    // The key of each weapon (its CustomWeapon key name), which is what abilities pass to mark().
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

    /** Per weapon key: its name, color and death messages. Filled by WeaponManager.register. */
    private final Map<String, Style> styles = new HashMap<>();

    private final Map<UUID, Mark> marks = new HashMap<>();

    /**
     * Adds a weapon's death messages. {@code weaponKey} is what abilities pass to {@link #mark}; the templates use
     * {@code <victim>}, {@code <killer>} and {@code <weapon>} and one is picked at random. With no templates the
     * generic message is used, with the weapon's name and color.
     */
    public void register(String weaponKey, String name, TextColor color, List<String> templates) {
        styles.put(weaponKey, new Style(name, color, List.copyOf(templates)));
    }

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

    private Component forWeapon(Player victim, Player killer, String weapon) {
        Style style = styles.get(weapon);
        String template = GENERIC;
        Component weaponName = Component.text(weapon);
        if (style != null) {
            if (!style.templates().isEmpty()) {
                template = style.templates().get(ThreadLocalRandom.current().nextInt(style.templates().size()));
            }
            weaponName = Component.text(style.name(), style.color());
        }
        return MINI.deserialize(template,
                Placeholder.component("victim", victim.name()),
                Placeholder.component("killer", killer.name()),
                Placeholder.component("weapon", weaponName));
    }
}
