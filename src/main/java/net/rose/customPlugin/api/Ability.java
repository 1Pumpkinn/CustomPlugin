package net.rose.customPlugin.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** One line of a weapon's lore: a colored trigger ("Offhand", "Passive"...) followed by a grey description. */
public record Ability(String trigger, String description) {

    public static Ability of(String trigger, String description) {
        return new Ability(trigger, description);
    }

    /** The offhand key (F) while holding the weapon. */
    public static Ability offhand(String description) {
        return new Ability("Offhand", description);
    }

    /** Always on, no input needed. */
    public static Ability passive(String description) {
        return new Ability("Passive", description);
    }

    /** Right-click. */
    public static Ability use(String description) {
        return new Ability("Use", description);
    }

    public Component toLore(TextColor color) {
        return Component.text(trigger, color)
                .append(Component.text(" - " + description, NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }
}
