package net.rose.customPlugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.shop.Points;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /points shows your balance. /points give|take|set <player> <amount> is for admins. */
public final class PointsCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "customplugin.points.admin";
    private static final List<String> ACTIONS = List.of("give", "take", "set");

    private final Points points;

    public PointsCommand(Points points) {
        this.points = points;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                sender.sendMessage(Component.text("You have " + format(points.get(player.getUniqueId())) + ".",
                        NamedTextColor.GOLD));
            } else {
                sender.sendMessage(Component.text("Usage: /" + label + " <give|take|set> <player> <amount>",
                        NamedTextColor.RED));
            }
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            sender.sendMessage(Component.text("Usage: /" + label + " [give|take|set <player> <amount>]",
                    NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(Component.text("You can't change points.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /" + label + " " + action + " <player> <amount>",
                    NamedTextColor.RED));
            return true;
        }

        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            amount = -1;
        }
        if (amount < 0) {
            sender.sendMessage(Component.text("The amount has to be a whole number, 0 or more.",
                    NamedTextColor.RED));
            return true;
        }

        int balance = switch (action) {
            case "give" -> points.add(target.getUniqueId(), amount);
            case "take" -> points.add(target.getUniqueId(), -amount);
            default -> points.set(target.getUniqueId(), amount);
        };
        String name = target.getName() == null ? args[1] : target.getName();
        sender.sendMessage(Component.text(name + " now has " + format(balance) + ".", NamedTextColor.GREEN));

        if (target instanceof Player online && !online.equals(sender)) {
            online.sendMessage(Component.text("You now have " + format(balance) + ".", NamedTextColor.GOLD));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission(ADMIN_PERMISSION)) return out;

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String action : ACTIONS) {
                if (action.startsWith(prefix)) out.add(action);
            }
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
            }
        }
        return out;
    }

    /** An online player, or someone the server has seen before. Null if neither. */
    private static OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.hasPlayedBefore() ? cached : null;
    }

    private static String format(int amount) {
        return amount + (amount == 1 ? " point" : " points");
    }
}
