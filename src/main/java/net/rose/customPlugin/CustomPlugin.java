package net.rose.customPlugin;

import net.rose.customPlugin.command.ShopCommand;
import net.rose.customPlugin.command.WeaponPointsCommand;
import net.rose.customPlugin.gui.ShopMenu;
import net.rose.customPlugin.gui.WeaponPointsMenu;
import net.rose.customPlugin.command.WeaponCommand;
import net.rose.customPlugin.api.WeaponManager;
import net.rose.customPlugin.effect.HatEffects;
import net.rose.customPlugin.effect.VampireSweepEffect;
import net.rose.customPlugin.effect.TempestSpinEffect;
import net.rose.customPlugin.effect.AxeSpinEffect;
import net.rose.customPlugin.effect.ChainEffects;
import net.rose.customPlugin.effect.EruptionEffect;
import net.rose.customPlugin.effect.FrostEffects;
import net.rose.customPlugin.effect.PetalEffect;
import net.rose.customPlugin.effect.ZeusLightning;
import net.rose.customPlugin.item.Legendaries;
import net.rose.customPlugin.item.WeaponRegistry;
import net.rose.customPlugin.item.axe.VolcanicAxe;
import net.rose.customPlugin.item.bow.HuntersBow;
import net.rose.customPlugin.item.bow.TricksterBow;
import net.rose.customPlugin.item.disc.ZeusDisc;
import net.rose.customPlugin.item.mace.MetalicMace;
import net.rose.customPlugin.item.misc.Hat;
import net.rose.customPlugin.item.misc.KillToken;
import net.rose.customPlugin.item.scythe.BloodthirstScythe;
import net.rose.customPlugin.item.spear.FrostSpear;
import net.rose.customPlugin.item.sword.PetalBlade;
import net.rose.customPlugin.item.sword.TideBlade;
import net.rose.customPlugin.item.trident.TempestTrident;
import net.rose.customPlugin.listener.axe.VolcanicAxeListener;
import net.rose.customPlugin.listener.bow.TricksterBowListener;
import net.rose.customPlugin.listener.disc.ZeusDiscListener;
import net.rose.customPlugin.listener.mace.MetalicMaceListener;
import net.rose.customPlugin.listener.disc.HatListener;
import net.rose.customPlugin.listener.misc.StunListener;
import net.rose.customPlugin.listener.player.*;
import net.rose.customPlugin.listener.scythe.BloodthirstScytheListener;
import net.rose.customPlugin.listener.spear.FrostSpearListener;
import net.rose.customPlugin.listener.sword.PetalBladeListener;
import net.rose.customPlugin.listener.sword.TideBladeListener;
import net.rose.customPlugin.listener.trident.TempestTridentListener;
import net.rose.customPlugin.pack.ResourcePackManager;
import net.rose.customPlugin.command.ReloadCommand;
import net.rose.customPlugin.command.WeaponPackCommand;
import net.rose.customPlugin.util.ConfigMigrator;
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
    private WeaponManager weaponManager;
    private ZeusDisc zeusDisc;
    private ResourcePackManager packManager;
    private ZeusDiscListener zeusDiscListener;
    private ChainEffects chainEffects;
    private CooldownBars cooldownBars;
    private EruptionEffect eruptionEffect;
    private AxeSpinEffect axeSpinEffect;
    private PetalBlade petalBlade;
    private PetalEffect petalEffect;
    private BloodthirstScythe bloodthirstScythe;
    private TempestTrident tempestTrident;
    private TempestSpinEffect tempestSpin;
    private Hat hat;
    private HatEffects hatEffects;
    private HatListener hatListener;
    private DeathMessages deathMessages;
    private WeaponLimitListener weaponLimit;

    @Override
    public void onEnable() {
        // Creates config.yml, and upgrades an older one to the current layout (the old file is backed up).
        ConfigMigrator.run(this);
        // A config.yml that is missing a setting gets it added; values it already has are kept.
        getConfig().options().copyDefaults(true);
        saveConfig();
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
        this.bloodthirstScythe = new BloodthirstScythe(this);
        this.tempestTrident = new TempestTrident(this);
        this.tempestSpin = new TempestSpinEffect(this, deathMessages);
        this.hat = new Hat(this);
        this.hatEffects = new HatEffects(this, hat, deathMessages);

        getServer().getPluginManager().registerEvents(new KillTokenListener(killToken), this);
        getServer().getPluginManager().registerEvents(deathMessages, this);
        Legendaries legendaries = new Legendaries(this);
        WeaponRegistry weaponRegistry = new WeaponRegistry(tideBlade, volcanicAxe, frostSpear, metalicMace, tricksterBow, zeusDisc, petalBlade, bloodthirstScythe, tempestTrident, hat);

        // Weapons built on the API (see docs/ADDING_A_WEAPON.md) are registered here, in one line each. This has to
        // come before the weapon limit, dropped-item beacon and shop, which look weapons up when they start.
        this.weaponManager = new WeaponManager(this, legendaries, weaponRegistry, cooldownBars, deathMessages);
        weaponManager.register(new HuntersBow(this));

        getServer().getPluginManager().registerEvents(new LegendaryDeathListener(this, legendaries), this);
        this.weaponLimit = new WeaponLimitListener(this, legendaries);
        getServer().getPluginManager().registerEvents(weaponLimit, this);
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
        getServer().getPluginManager().registerEvents(new DroppedItemGlow(this, killToken, legendaries), this);
        getServer().getPluginManager().registerEvents(
                new VolcanicAxeListener(volcanicAxe, cooldownBars, eruptionEffect, axeSpinEffect), this);

        getServer().getPluginManager().registerEvents(new PetalBladeListener(this, petalBlade, cooldownBars, petalEffect), this);
        getServer().getPluginManager().registerEvents(
                new BloodthirstScytheListener(bloodthirstScythe, cooldownBars, new VampireSweepEffect(deathMessages)), this);
        getServer().getPluginManager().registerEvents(
                new TempestTridentListener(this, tempestTrident, cooldownBars, tempestSpin), this);
        this.hatListener = new HatListener(this, hat, cooldownBars, hatEffects, deathMessages);
        getServer().getPluginManager().registerEvents(hatListener, this);

        // The pack is bundled in this jar and served by a built-in HTTP server (see resource-pack in config.yml).
        this.packManager = new ResourcePackManager(this, getFile());
        packManager.start();
        PluginCommand packCommand = getCommand("weaponpack");
        if (packCommand != null) {
            WeaponPackCommand weaponPackCommand = new WeaponPackCommand(packManager);
            packCommand.setExecutor(weaponPackCommand);
            packCommand.setTabCompleter(weaponPackCommand);
        }

        PluginCommand reloadCommand = getCommand("customplugin");
        if (reloadCommand != null) {
            ReloadCommand reload = new ReloadCommand(this);
            reloadCommand.setExecutor(reload);
            reloadCommand.setTabCompleter(reload);
        }

        WeaponCommand weaponCommand = new WeaponCommand(weaponRegistry);
        PluginCommand command = getCommand("weapon");
        if (command != null) {
            command.setExecutor(weaponCommand);
            command.setTabCompleter(weaponCommand);
        }

        // Weapons keep the lore they were created with, so give existing ones the current description.
        getServer().getPluginManager().registerEvents(new WeaponLoreRefresher(weaponRegistry, legendaries), this);

        ShopMenu shopMenu = new ShopMenu(this, weaponRegistry, weaponLimit, killToken);
        getServer().getPluginManager().registerEvents(shopMenu, this);
        PluginCommand shopCommand = getCommand("shop");
        if (shopCommand != null) {
            shopCommand.setExecutor(new ShopCommand(shopMenu));
        }

        WeaponPointsMenu weaponPointsMenu = new WeaponPointsMenu(this, weaponLimit, shopMenu);
        getServer().getPluginManager().registerEvents(weaponPointsMenu, this);
        PluginCommand weaponPointsCommand = getCommand("weaponpoints");
        if (weaponPointsCommand != null) {
            weaponPointsCommand.setExecutor(new WeaponPointsCommand(weaponPointsMenu));
        }
    }

    @Override
    public void onDisable() {
        if (packManager != null) packManager.stop();
        if (weaponLimit != null) weaponLimit.shutdown();
        if (zeusDiscListener != null) zeusDiscListener.shutdown();
        if (hatListener != null) hatListener.shutdown();
        if (cooldownBars != null) cooldownBars.shutdown();
        if (eruptionEffect != null) eruptionEffect.removeAll();
        if (axeSpinEffect != null) axeSpinEffect.removeAll();
        if (petalEffect != null) petalEffect.removeAll();
        if (tempestSpin != null) tempestSpin.removeAll();
        if (hatEffects != null) hatEffects.removeAll();
        if (frostEffects != null) frostEffects.shutdown();
        if (chainEffects != null) chainEffects.shutdown();
        if (weaponManager != null) weaponManager.shutdown();
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

    public Hat getHat() {
        return hat;
    }

    public BloodthirstScythe getBloodthirstScythe() {
        return bloodthirstScythe;
    }

    public TempestTrident getTempestTrident() {
        return tempestTrident;
    }
}
