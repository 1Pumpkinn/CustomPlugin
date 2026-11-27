package net.rose.customPlugin;

import net.rose.customPlugin.command.WeaponCommand;
import net.rose.customPlugin.item.KillToken;
import net.rose.customPlugin.effect.AxeSpinEffect;
import net.rose.customPlugin.effect.ChainEffects;
import net.rose.customPlugin.effect.EruptionEffect;
import net.rose.customPlugin.effect.FrostEffects;
import net.rose.customPlugin.effect.PetalEffect;
import net.rose.customPlugin.effect.ZeusLightning;
import net.rose.customPlugin.item.FrostSpear;
import net.rose.customPlugin.item.Legendaries;
import net.rose.customPlugin.item.MetalicMace;
import net.rose.customPlugin.item.PetalBlade;
import net.rose.customPlugin.item.TricksterBow;
import net.rose.customPlugin.item.TideBlade;
import net.rose.customPlugin.item.VolcanicAxe;
import net.rose.customPlugin.item.ZeusDisc;
import net.rose.customPlugin.listener.DroppedItemGlow;
import net.rose.customPlugin.listener.EgapListener;
import net.rose.customPlugin.listener.FrostSpearListener;
import net.rose.customPlugin.listener.KillTokenListener;
import net.rose.customPlugin.listener.LegendaryDeathListener;
import net.rose.customPlugin.listener.LungeCooldown;
import net.rose.customPlugin.listener.MaceDamageCap;
import net.rose.customPlugin.listener.MetalicMaceListener;
import net.rose.customPlugin.listener.PetalBladeListener;
import net.rose.customPlugin.listener.StunListener;
import net.rose.customPlugin.listener.TideBladeListener;
import net.rose.customPlugin.listener.TricksterBowListener;
import net.rose.customPlugin.listener.VolcanicAxeListener;
import net.rose.customPlugin.listener.ZeusDiscListener;
import net.rose.customPlugin.pack.ResourcePackManager;
import net.rose.customPlugin.command.WeaponPackCommand;
import net.rose.customPlugin.util.CooldownBars;
import net.rose.customPlugin.util.DeathMessages;
import net.rose.customPlugin.util.TrueDamage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomPlugin extends JavaPlugin {

    private KillToken killToken;
    private TideBlade tideBlade;
    private VolcanicAxe volcanicAxe;
    private FrostSpear frostSpear;
    private FrostEffects frostEffects;
    private MetalicMace metalicMace;
    private TricksterBow tricksterBow;
    private ZeusDisc zeusDisc;
    private ResourcePackManager packManager;
    private ZeusDiscListener zeusDiscListener;
    private ChainEffects chainEffects;
    private CooldownBars cooldownBars;
    private EruptionEffect eruptionEffect;
    private AxeSpinEffect axeSpinEffect;
    private PetalBlade petalBlade;
    private PetalEffect petalEffect;
    private DeathMessages deathMessages;

    @Override
    public void onEnable() {
        new TrueDamage(this); // registers its listener; ability damage ignores armor, enchants and resistance
        this.killToken = new KillToken(this);
        this.tideBlade = new TideBlade(this);
        this.volcanicAxe = new VolcanicAxe(this);
        this.frostSpear = new FrostSpear(this);
        this.deathMessages = new DeathMessages();
        this.frostEffects = new FrostEffects(this, deathMessages);
        this.metalicMace = new MetalicMace(this);
        this.tricksterBow = new TricksterBow(this);
        this.zeusDisc = new ZeusDisc(this);
        this.chainEffects = new ChainEffects(this);
        this.cooldownBars = new CooldownBars(this);
        this.eruptionEffect = new EruptionEffect(this, deathMessages);
        this.axeSpinEffect = new AxeSpinEffect(this, deathMessages);
        this.petalBlade = new PetalBlade(this);
        this.petalEffect = new PetalEffect(this, deathMessages);

        getServer().getPluginManager().registerEvents(new KillTokenListener(killToken), this);
        getServer().getPluginManager().registerEvents(deathMessages, this);
        getServer().getPluginManager().registerEvents(new LegendaryDeathListener(this, new Legendaries(this)), this);
        getServer().getPluginManager().registerEvents(new TideBladeListener(this, tideBlade, cooldownBars, deathMessages), this);
        getServer().getPluginManager().registerEvents(new EgapListener(), this);
        getServer().getPluginManager().registerEvents(new StunListener(frostEffects), this);
        getServer().getPluginManager().registerEvents(
                new FrostSpearListener(this, frostSpear, cooldownBars, frostEffects), this);
        getServer().getPluginManager().registerEvents(
                new MetalicMaceListener(this, metalicMace, cooldownBars, chainEffects), this);
        getServer().getPluginManager().registerEvents(new TricksterBowListener(this, tricksterBow, cooldownBars), this);
        this.zeusDiscListener = new ZeusDiscListener(this, zeusDisc, cooldownBars,
                new ZeusLightning(this, deathMessages), deathMessages);
        getServer().getPluginManager().registerEvents(zeusDiscListener, this);
        new LungeCooldown(this).register();
        getServer().getPluginManager().registerEvents(new MaceDamageCap(), this);
        getServer().getPluginManager().registerEvents(new DroppedItemGlow(this), this);
        getServer().getPluginManager().registerEvents(
                new VolcanicAxeListener(volcanicAxe, cooldownBars, eruptionEffect, axeSpinEffect), this);

        getServer().getPluginManager().registerEvents(new PetalBladeListener(petalBlade, cooldownBars, petalEffect), this);

        saveDefaultConfig();
        // The pack is bundled in this jar and served by a built-in HTTP server (see resource-pack in config.yml).
        this.packManager = new ResourcePackManager(this, getFile());
        packManager.start();
        PluginCommand packCommand = getCommand("weaponpack");
        if (packCommand != null) {
            WeaponPackCommand weaponPackCommand = new WeaponPackCommand(packManager);
            packCommand.setExecutor(weaponPackCommand);
            packCommand.setTabCompleter(weaponPackCommand);
        }

        WeaponCommand weaponCommand = new WeaponCommand(tideBlade, volcanicAxe, frostSpear, metalicMace, tricksterBow, zeusDisc, petalBlade);
        PluginCommand command = getCommand("weapon");
        if (command != null) {
            command.setExecutor(weaponCommand);
            command.setTabCompleter(weaponCommand);
        }
    }

    @Override
    public void onDisable() {
        if (packManager != null) packManager.stop();
        if (zeusDiscListener != null) zeusDiscListener.shutdown();
        if (cooldownBars != null) cooldownBars.shutdown();
        if (eruptionEffect != null) eruptionEffect.removeAll();
        if (axeSpinEffect != null) axeSpinEffect.removeAll();
        if (petalEffect != null) petalEffect.removeAll();
        if (frostEffects != null) frostEffects.shutdown();
        if (chainEffects != null) chainEffects.shutdown();
    }

    public KillToken getKillToken() {
        return killToken;
    }

    public VolcanicAxe getVolcanicAxe() {
        return volcanicAxe;
    }

    public FrostSpear getFrostSpear() {
        return frostSpear;
    }

    public MetalicMace getMetalicMace() {
        return metalicMace;
    }

    public TideBlade getTideBlade() {
        return tideBlade;
    }

    public PetalBlade getPetalBlade() {
        return petalBlade;
    }
}
