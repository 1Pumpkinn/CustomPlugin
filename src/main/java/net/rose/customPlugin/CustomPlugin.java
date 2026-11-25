package net.rose.customPlugin;

import net.rose.customPlugin.command.WeaponCommand;
import net.rose.customPlugin.item.KillToken;
import net.rose.customPlugin.item.TideBlade;
import net.rose.customPlugin.listener.KillTokenListener;
import net.rose.customPlugin.listener.TideBladeListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomPlugin extends JavaPlugin {

    private KillToken killToken;
    private TideBlade tideBlade;

    @Override
    public void onEnable() {
        this.killToken = new KillToken(this);
        this.tideBlade = new TideBlade(this);

        getServer().getPluginManager().registerEvents(new KillTokenListener(killToken), this);
        getServer().getPluginManager().registerEvents(new TideBladeListener(this, tideBlade), this);

        WeaponCommand weaponCommand = new WeaponCommand(tideBlade);
        PluginCommand command = getCommand("weapon");
        if (command != null) {
            command.setExecutor(weaponCommand);
            command.setTabCompleter(weaponCommand);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public KillToken getKillToken() {
        return killToken;
    }

    public TideBlade getTideBlade() {
        return tideBlade;
    }
}
