package net.rose.customPlugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.pack.ResourcePackManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /weaponpack - sends you the weapon texture pack again.
 * /weaponpack status - shows the built-in pack server's URL, hash and size.
 * /weaponpack reload - re-reads config.yml, rebuilds the pack and resends it to everyone.
 */
public final class WeaponPackCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "customplugin.weaponpack.reload";

    private final ResourcePackManager packManager;

    public WeaponPackCommand(ResourcePackManager packManager) {
        this.packManager = packManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";

        if (action.equals("reload") || action.equals("status")) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(Component.text("You can't use that.", NamedTextColor.RED));
                return true;
            }
            if (action.equals("reload")) {
                packManager.reload();
                sender.sendMessage(packManager.isRunning()
                        ? Component.text("Resource pack rebuilt and resent to everyone online.", NamedTextColor.GREEN)
                        : Component.text("Resource pack is not running - check the console.", NamedTextColor.RED));
            } else {
                status(sender);
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can receive the pack. Use /" + label + " reload.",
                    NamedTextColor.RED));
            return true;
        }
        if (!packManager.isRunning()) {
            player.sendMessage(Component.text("The texture pack is not running on this server.", NamedTextColor.RED));
            return true;
        }
        packManager.send(player);
        player.sendMessage(Component.text("Sending the weapon texture pack...", NamedTextColor.GREEN));
        return true;
    }

    private void status(CommandSender sender) {
        if (!packManager.isRunning()) {
            sender.sendMessage(Component.text(
                    "Resource pack is not running (disabled, bad config or the port is taken - see the console).",
                    NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("Resource pack is live", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("  URL: " + packManager.getUrl(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  SHA-1: " + packManager.getSha1(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  Size: " + packManager.getSizeBytes() / 1024 + " KB", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(ADMIN_PERMISSION)) {
            List<String> options = new ArrayList<>();
            for (String option : List.of("status", "reload")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) options.add(option);
            }
            return options;
        }
        return List.of();
    }
}
