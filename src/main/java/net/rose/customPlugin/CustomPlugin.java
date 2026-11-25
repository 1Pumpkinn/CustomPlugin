package net.rose.customPlugin;

import net.rose.customPlugin.item.KillToken;
import net.rose.customPlugin.listener.KillTokenListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomPlugin extends JavaPlugin {

    private KillToken killToken;

    @Override
    public void onEnable() {
        this.killToken = new KillToken(this);
        getServer().getPluginManager().registerEvents(new KillTokenListener(killToken), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public KillToken getKillToken() {
        return killToken;
    }
}
