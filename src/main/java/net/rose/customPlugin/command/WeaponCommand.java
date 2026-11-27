package net.rose.customPlugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.item.BloodthirstScythe;
import net.rose.customPlugin.item.FrostSpear;
import net.rose.customPlugin.item.Hat;
import net.rose.customPlugin.item.MetalicMace;
import net.rose.customPlugin.item.PetalBlade;
import net.rose.customPlugin.item.TideBlade;
import net.rose.customPlugin.item.TricksterBow;
import net.rose.customPlugin.item.VolcanicAxe;
import net.rose.customPlugin.item.ZeusDisc;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** /weapon <name> [player] - gives a custom weapon. */
public final class WeaponCommand implements CommandExecutor, TabCompleter {

    private final Map<String, Supplier<ItemStack>> weapons = new LinkedHashMap<>();

    public WeaponCommand(TideBlade tideBlade, VolcanicAxe volcanicAxe, FrostSpear frostSpear, MetalicMace metalicMace, TricksterBow tricksterBow, ZeusDisc zeusDisc,
                         PetalBlade petalBlade, BloodthirstScythe bloodthirstScythe, Hat hat) {
        weapons.put("tideblade", tideBlade::create);
        weapons.put("volcanicaxe", volcanicAxe::create);
        weapons.put("frostspear", frostSpear::create);
        weapons.put("metalicmace", metalicMace::create);
        weapons.put("tricksterbow", tricksterBow::create);
        weapons.put("zeusdisc", zeusDisc::create);
        weapons.put("petalblade", petalBlade::create);
        weapons.put("bloodthirstscythe", bloodthirstScythe::create);
        weapons.put("hat", hat::create);
        // Register future weapons here: weapons.put("name", item::create);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /" + label + " <" + String.join("|", weapons.keySet())
                    + "> [player]", NamedTextColor.RED));
            return true;
        }

        Supplier<ItemStack> supplier = weapons.get(args[0].toLowerCase(Locale.ROOT));
        if (supplier == null) {
            sender.sendMessage(Component.text("Unknown weapon. Available: " + String.join(", ", weapons.keySet()),
                    NamedTextColor.RED));
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Component.text("Specify a player: /" + label + " " + args[0] + " <player>",
                    NamedTextColor.RED));
            return true;
        }

        // Full inventory -> the leftover drops at the target's feet
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(supplier.get());
        for (ItemStack stack : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), stack);
        }

        sender.sendMessage(Component.text("Gave " + args[0].toLowerCase(Locale.ROOT) + " to " + target.getName(),
                NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String name : weapons.keySet()) {
                if (name.startsWith(prefix)) out.add(name);
            }
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
            }
        }
        return out;
    }
}
