package net.rose.customPlugin;

import net.rose.customPlugin.command.ShopCommand;
import net.rose.customPlugin.command.WeaponPointsCommand;
import net.rose.customPlugin.gui.ShopMenu;
import net.rose.customPlugin.gui.WeaponPointsMenu;
import net.rose.customPlugin.command.WeaponCommand;
import net.rose.customPlugin.api.WeaponManager;
import net.rose.customPlugin.effect.FrostEffects;
import net.rose.customPlugin.item.Legendaries;
import net.rose.customPlugin.item.WeaponLimit;
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
import net.rose.customPlugin.listener.misc.StunListener;
import net.rose.customPlugin.listener.player.*;
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
    private WeaponManager weaponManager;
    private ResourcePackManager packManager;
    private CooldownBars cooldownBars;
    private FrostEffects frostEffects;
    private WeaponLimit weaponLimit;
    private WeaponLimitNotifier limitNotifier;
    private CombatListener combat;

    @Override
    public void onEnable() {
        // Creates config.yml, and upgrades an older one to the current layout (the old file is backed up).
        ConfigMigrator.run(this);
        // A config.yml that is missing a setting gets it added; values it already has are kept.
        getConfig().options().copyDefaults(true);
        saveConfig();
        new TrueDamage(this); // registers its listener; ability damage ignores armor, enchants and resistance
        this.killToken = new KillToken(this);
        DeathMessages deathMessages = new DeathMessages();
        this.frostEffects = new FrostEffects(this, deathMessages); // shared by the Frost Spear and the stun listener
        this.cooldownBars = new CooldownBars(this);

        getServer().getPluginManager().registerEvents(new KillTokenListener(killToken), this);
        getServer().getPluginManager().registerEvents(deathMessages, this);
        Legendaries legendaries = new Legendaries();
        WeaponRegistry weaponRegistry = new WeaponRegistry();

        // Every weapon is built on the API (see docs/ADDING_A_WEAPON.md) and registered here, in one line each: the
        // /weapon command, shop, weapon limit, beacon, lore, config entry and listener are all wired by register().
        // This has to come before the weapon limit, dropped-item beacon and shop, which look weapons up when they start.
        // The order is the order of /weapon, the shop and the weapon lists.
        this.weaponManager = new WeaponManager(this, legendaries, weaponRegistry, cooldownBars, deathMessages);
        weaponManager.register(new TideBlade(this));
        weaponManager.register(new VolcanicAxe(this));
        weaponManager.register(new FrostSpear(this, frostEffects));
        weaponManager.register(new MetalicMace(this));
        weaponManager.register(new TricksterBow(this));
        weaponManager.register(new ZeusDisc(this));
        weaponManager.register(new PetalBlade(this));
        weaponManager.register(new BloodthirstScythe(this));
        weaponManager.register(new TempestTrident(this));
        weaponManager.register(new Hat(this));
        weaponManager.register(new HuntersBow(this));

        getServer().getPluginManager().registerEvents(new LegendaryDeathListener(this, legendaries), this);
        this.weaponLimit = new WeaponLimit(this, legendaries);
        this.limitNotifier = new WeaponLimitNotifier(this, weaponLimit);
        getServer().getPluginManager().registerEvents(new WeaponLimitListener(weaponLimit, limitNotifier), this);
        this.combat = new CombatListener(this);
        getServer().getPluginManager().registerEvents(combat, this);
        getServer().getPluginManager().registerEvents(new EgapListener(), this);
        getServer().getPluginManager().registerEvents(new StunListener(frostEffects), this);
        new LungeCooldown(this).register();
        getServer().getPluginManager().registerEvents(new MaceDamageCap(), this);
        getServer().getPluginManager().registerEvents(new DroppedItemGlow(this, killToken, legendaries), this);

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
        if (limitNotifier != null) limitNotifier.shutdown();
        if (combat != null) combat.shutdown();
        if (weaponLimit != null) weaponLimit.shutdown();
        if (cooldownBars != null) cooldownBars.shutdown();
        if (frostEffects != null) frostEffects.shutdown();
        // Each weapon ends what it has running: flights, displays, tasks and its effects.
        if (weaponManager != null) weaponManager.shutdown();
    }

    public KillToken getKillToken() {
        return killToken;
    }
}
