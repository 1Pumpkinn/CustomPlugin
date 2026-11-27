package net.rose.customPlugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.shop.WeaponShop;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /shop - opens the weapon shop. */
public final class ShopCommand implements CommandExecutor {

    private final WeaponShop shop;

    public ShopCommand(WeaponShop shop) {
        this.shop = shop;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the shop.", NamedTextColor.RED));
            return true;
        }
        shop.open(player);
        return true;
    }
}
