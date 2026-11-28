package net.rose.customPlugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * /customplugin reload - re-reads config.yml. Weapon tiers, points, prices and the Hat / Petal Blade settings are read
 * from the config whenever they are used, so they apply straight away. (Resource pack and dropped-item beacon settings:
 * use /weaponpack reload or restart.)
 *
 * <p>/customplugin reset - puts the default settings back, either everything or one section. Always asks for
 * "confirm" first, and the current config.yml is saved as config-backup-&lt;time&gt;.yml before it is changed.
 */
public final class ReloadCommand implements CommandExecutor, TabCompleter {

    /** The sections of config.yml that can be reset on their own. */
    private static final List<String> SECTIONS = List.of(
            "weapons", "weapon-limit", "shop", "hat", "petal-blade", "dropped-item-beacon", "resource-pack");

    private final JavaPlugin plugin;

    public ReloadCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (action.equals("reload")) {
            plugin.reloadConfig();
            send(sender, "config.yml reloaded: weapon tiers, points, prices and the Hat and Petal Blade settings are live.",
                    NamedTextColor.GREEN);
        } else if (action.equals("reset")) {
            reset(sender, label, args);
        } else {
            send(sender, "Usage: /" + label + " reload  |  /" + label + " reset <all|section> confirm", NamedTextColor.YELLOW);
        }
        return true;
    }

    private void reset(CommandSender sender, String label, String[] args) {
        String target = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        boolean confirmed = args.length >= 3 && args[2].equalsIgnoreCase("confirm");

        // "/customplugin reset confirm" is the same as "reset all confirm".
        if (target.equals("confirm")) {
            target = "all";
            confirmed = true;
        }
        if (!target.equals("all") && !SECTIONS.contains(target)) {
            send(sender, "Usage: /" + label + " reset <all|" + String.join("|", SECTIONS) + "> confirm", NamedTextColor.YELLOW);
            return;
        }
        if (!confirmed) {
            send(sender, "This puts the default " + (target.equals("all") ? "config.yml" : "'" + target + "' settings")
                    + " back (your current file is backed up first). Run /" + label + " reset " + target
                    + " confirm to do it.", NamedTextColor.YELLOW);
            return;
        }

        File file = new File(plugin.getDataFolder(), "config.yml");
        File backup = new File(plugin.getDataFolder(),
                "config-backup-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".yml");
        try {
            if (file.exists()) Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            send(sender, "Could not back up config.yml, nothing was changed: " + e.getMessage(), NamedTextColor.RED);
            return;
        }

        if (target.equals("all")) {
            plugin.saveResource("config.yml", true);
            plugin.reloadConfig();
        } else if (!resetSection(target)) {
            send(sender, "Could not read the default config from the plugin jar, nothing was changed.", NamedTextColor.RED);
            return;
        }
        send(sender, (target.equals("all") ? "config.yml" : "'" + target + "'") + " is back to the defaults (old one saved as "
                + backup.getName() + "). Settings are live now.", NamedTextColor.GREEN);
        if (target.equals("all") || target.equals("resource-pack")) {
            send(sender, "The resource-pack host is back to 'localhost': set it again, then run /weaponpack reload.",
                    NamedTextColor.GOLD);
        }
    }

    /** Puts one section back to the jar's defaults: its default values are written and anything extra is removed. */
    private boolean resetSection(String section) {
        InputStream stream = plugin.getResource("config.yml");
        if (stream == null) return false;
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection defaultSection = defaults.getConfigurationSection(section);
        if (defaultSection == null) return false;

        FileConfiguration config = plugin.getConfig();
        ConfigurationSection current = config.getConfigurationSection(section);
        if (current != null) {
            for (String key : new ArrayList<>(current.getKeys(true))) {
                if (!current.isConfigurationSection(key) && !defaultSection.isSet(key)) current.set(key, null);
            }
        }
        for (String key : defaultSection.getKeys(true)) {
            if (!defaultSection.isConfigurationSection(key)) config.set(section + "." + key, defaultSection.get(key));
        }
        plugin.saveConfig();
        return true;
    }

    private static void send(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(Component.text(text, color));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("reload", "reset"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            options.add("all");
            options.addAll(SECTIONS);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            options.add("confirm");
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(typed));
        return options;
    }
}
