# Adding a weapon

A weapon is two small classes plus its resource pack files. Everything else (the `/weapon` command, the shop,
the weapon limit, the dropped-item beacon, lore refreshing, the `weapons.<id>` entry in `config.yml`) is wired up
for you by `WeaponManager.register`.

Every weapon in the plugin is built this way. The Hunter's Bow (`item/bow/HuntersBow.java` +
`listener/bow/HuntersBowListener.java`) is the smallest example; the Volcanic Axe (attributes, two effects owned by
the weapon) and the Zeus Disc (data components, a listener that needs shutting down) show the hooks below.

## 1. The item: extend `CustomWeapon`

```java
public final class MyBow extends CustomWeapon {

    public static final TextColor COLOR = TextColor.color(0x55FF55);

    public MyBow(JavaPlugin plugin) {
        //     plugin  /weapon id  model + tag name  display name  color  base item
        super(plugin, "mybow",     "my_bow",         "My Bow",     COLOR, Material.BOW);
    }

    @Override
    public List<Ability> abilities() {
        return List.of(Ability.offhand("Does a thing"), Ability.passive("Does another thing"));
    }

    @Override
    public Listener createListener(WeaponContext context) {
        return new MyBowListener(context, this);
    }
}
```

| Member                         | What it does                                                                  |
|--------------------------------|-------------------------------------------------------------------------------|
| `abilities()`                  | The lore lines. `Ability.offhand / passive / use / of(trigger, text)`.        |
| `createListener(ctx)`          | Your abilities. Return `null` if the weapon has none.                         |
| `defaultTier()` / `defaultPrice()` | Written to `config.yml` the first time (defaults: tier 2, price 32). The server owner edits them there afterwards. |
| `customize(ItemMeta)`          | Attributes, flags, unbreakable, etc.                                          |
| `finish(ItemStack)`            | Runs on the finished item, for what the meta can't reach (data components such as `JUKEBOX_PLAYABLE`). |
| `maxEnchanted()`               | `false` to skip the automatic max enchants.                                   |
| `skippedEnchants()`            | Enchantment keys the automatic max enchants leave off (e.g. `Set.of("fire_aspect")`). |
| `onDisable()`                  | End anything the weapon keeps running (displays, tasks, effects).             |
| `isWeapon(ItemStack)`          | Is this stack your weapon? Null-safe.                                         |

The `id` is lower case with no spaces. It is the `/weapon` name and the key under `weapons:` in `config.yml`.

## 2. The abilities: extend `AbilityListener`

```java
public final class MyBowListener extends AbilityListener<MyBow> {

    private static final String PULSE_ID = "my_pulse";

    public MyBowListener(WeaponContext context, MyBow weapon) {
        super(context, weapon);
    }

    @Override // F with the weapon in hand; the swap is already cancelled
    protected void onOffhand(Player player, boolean sneaking) {
        if (!ready(player, PULSE_ID)) return;            // the boss bar already shows the time left
        // ... do the thing ...
        startCooldown(player, PULSE_ID, "My Pulse", BossBar.Color.GREEN, 30_000);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        // weapon.isWeapon(item) tells you whether it was your weapon
    }

    @Override protected void onPlayerQuit(Player player)  { /* remove this player from your maps */ }
    @Override protected void onPlayerDeath(Player player) { /* drop primed state */ }
}
```

The base class already handles: the offhand key, restoring cooldown boss bars on join, hiding them on quit.
`onPlayerJoin(player)` runs right after the bars are restored, for anything else a weapon shows on join.
`usesOffhand()` returns `false` if the weapon does not use F (so F is left alone).

Helpers: `key(name)` (a namespaced key for tagging arrows or entities), `ready(player, id)`,
`startCooldown(...)`. `cooldowns` (primed bars: `prime / updatePrimed / unprime`), `plugin` and `weapon` are fields.

### Effects the weapon owns

A weapon that has an effect class (`EruptionEffect`, `PetalEffect`, ...) creates it in `createListener`, hands it to
its listener, and ends it in `onDisable()` (see `VolcanicAxe`, `PetalBlade`, `Hat`). The plugin then has nothing
weapon-specific to start or stop. An effect that something other than the weapon also uses (the Frost Spear's
`FrostEffects` is shared with the stun listener) is created by the plugin and passed to the weapon's constructor.

## 3. Register it

One line in `CustomPlugin.onEnable`, next to the Hunter's Bow:

```java
weaponManager.register(new MyBow(this));
```

## 4. Resource pack files

Under `src/main/resources/customplugin/assets/customplugin/` (the model name is the `keyName` from step 1):

- `textures/item/my_bow.png` (and `my_bow_pulling_0/1/2.png` for a bow)
- `models/item/my_bow.json` (and `my_bow_pulling_0/1/2.json`)
- `items/my_bow.json`

For a bow, copy the six `hunters_bow*` files and rename them. For other items copy the matching existing weapon.
No Java change is needed: the pack archive picks up everything in that folder.

## 5. Check before you ship: the leak checklist

- Every `Map<UUID, ...>` / `Set<UUID>` you add is cleared in `onPlayerQuit` (and `onPlayerDeath` if it should not
  survive death).
- Every `BukkitRunnable` you start is cancelled when its target dies, leaves or the effect ends, and checks
  `Targets.isGone(entity)` / `player.isOnline()` each tick.
- Every `Display` entity you spawn is `setPersistent(false)` and removed in `onDisable()` (so a restart or reload
  leaves none behind).
- Anything keyed by an *entity* (an arrow, a trident) is tagged with persistent data instead of held in a map, or
  the map entry has a timeout.
- An item you take from a player is always handed back or dropped, even if the player quits meanwhile.

## What is not automatic yet

- Death messages: `DeathMessages` has a style per built-in weapon. A new weapon falls back to the generic message
  with its name. Add a style there for a custom one.
