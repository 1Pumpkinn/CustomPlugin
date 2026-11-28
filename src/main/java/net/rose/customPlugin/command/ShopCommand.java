package net.rose.customPlugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.gui.ShopMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /shop - opens the weapon shop, where weapons are bought with kill tokens. */
public final class ShopCommand implements CommandExecutor {

    private final ShopMenu menu;

    public ShopCommand(ShopMenu menu) {
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can shop.", NamedTextColor.RED));
            return true;
        }
        menu.open(player);
        return true;
    }
}
