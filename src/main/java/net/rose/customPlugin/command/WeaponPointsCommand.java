package net.rose.customPlugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.gui.WeaponPointsMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /weaponpoints - opens the weapon points menu. */
public final class WeaponPointsCommand implements CommandExecutor {

    private final WeaponPointsMenu menu;

    public WeaponPointsCommand(WeaponPointsMenu menu) {
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players have weapon points.", NamedTextColor.RED));
            return true;
        }
        menu.open(player);
        return true;
    }
}
