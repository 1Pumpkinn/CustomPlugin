package net.rose.customPlugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.pack.ResourcePackAssigner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /weaponpack - sends you the weapon texture pack again. /weaponpack reload - re-reads the config and resends it to everyone. */
public final class WeaponPackCommand implements CommandExecutor, TabCompleter {

    private final ResourcePackAssigner assigner;

    public WeaponPackCommand(ResourcePackAssigner assigner) {
        this.assigner = assigner;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("customplugin.weaponpack.reload")) {
                sender.sendMessage(Component.text("You can't reload the texture pack.", NamedTextColor.RED));
                return true;
            }
            assigner.load();
            assigner.sendToAll();
            sender.sendMessage(Component.text(assigner.isEnabled()
                    ? "Reloaded the texture pack config and resent the pack to everyone online."
                    : "Reloaded, but the texture pack is disabled (see config.yml).", NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can receive the pack. Use /" + label + " reload.",
                    NamedTextColor.RED));
            return true;
        }
        if (!assigner.isEnabled()) {
            player.sendMessage(Component.text("The texture pack is disabled on this server.", NamedTextColor.RED));
            return true;
        }
        assigner.send(player);
        player.sendMessage(Component.text("Sending the weapon texture pack...", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("customplugin.weaponpack.reload")
                && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return List.of();
    }
}
