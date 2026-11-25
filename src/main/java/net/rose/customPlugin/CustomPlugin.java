package net.rose.customPlugin;

import net.rose.customPlugin.command.WeaponCommand;
import net.rose.customPlugin.item.KillToken;
import net.rose.customPlugin.effect.AxeSpinEffect;
import net.rose.customPlugin.effect.EruptionEffect;
import net.rose.customPlugin.item.TideBlade;
import net.rose.customPlugin.item.VolcanicAxe;
import net.rose.customPlugin.listener.EgapListener;
import net.rose.customPlugin.listener.KillTokenListener;
import net.rose.customPlugin.listener.TideBladeListener;
import net.rose.customPlugin.listener.VolcanicAxeListener;
import net.rose.customPlugin.util.CooldownBars;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomPlugin extends JavaPlugin {

    private KillToken killToken;
    private TideBlade tideBlade;
    private VolcanicAxe volcanicAxe;
    private CooldownBars cooldownBars;
    private EruptionEffect eruptionEffect;
    private AxeSpinEffect axeSpinEffect;

    @Override
    public void onEnable() {
        this.killToken = new KillToken(this);
        this.tideBlade = new TideBlade(this);
        this.volcanicAxe = new VolcanicAxe(this);
        this.cooldownBars = new CooldownBars(this);
        this.eruptionEffect = new EruptionEffect(this);
        this.axeSpinEffect = new AxeSpinEffect(this);

        getServer().getPluginManager().registerEvents(new KillTokenListener(killToken), this);
        getServer().getPluginManager().registerEvents(new TideBladeListener(this, tideBlade, cooldownBars), this);
        getServer().getPluginManager().registerEvents(new EgapListener(cooldownBars), this);
        getServer().getPluginManager().registerEvents(
                new VolcanicAxeListener(volcanicAxe, cooldownBars, eruptionEffect, axeSpinEffect), this);

        WeaponCommand weaponCommand = new WeaponCommand(tideBlade, volcanicAxe);
        PluginCommand command = getCommand("weapon");
        if (command != null) {
            command.setExecutor(weaponCommand);
            command.setTabCompleter(weaponCommand);
        }
    }

    @Override
    public void onDisable() {
        if (cooldownBars != null) cooldownBars.shutdown();
        if (eruptionEffect != null) eruptionEffect.removeAll();
        if (axeSpinEffect != null) axeSpinEffect.removeAll();
    }

    public KillToken getKillToken() {
        return killToken;
    }

    public VolcanicAxe getVolcanicAxe() {
        return volcanicAxe;
    }

    public TideBlade getTideBlade() {
        return tideBlade;
    }
}
