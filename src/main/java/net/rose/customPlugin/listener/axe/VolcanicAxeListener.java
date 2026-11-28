package net.rose.customPlugin.listener.axe;

import net.rose.customPlugin.util.Aim;
import net.rose.customPlugin.api.WeaponContext;
import net.rose.customPlugin.api.AbilityListener;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.customPlugin.effect.AxeSpinEffect;
import net.rose.customPlugin.effect.EruptionEffect;
import net.rose.customPlugin.item.axe.VolcanicAxe;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class VolcanicAxeListener extends AbilityListener<VolcanicAxe> {

    private static final double ERUPTION_RANGE = 30.0;
    private static final double RAY_SIZE = 0.8;
    private static final long ERUPTION_COOLDOWN_MS = 30_000;
    private static final long SPIN_COOLDOWN_MS = 45_000;

    private static final String ERUPTION_ID = "volcanic_eruption";
    private static final String SPIN_ID = "volcanic_spin";

    private final EruptionEffect eruption;
    private final AxeSpinEffect spin;

    public VolcanicAxeListener(WeaponContext context, VolcanicAxe axe, EruptionEffect eruption, AxeSpinEffect spin) {
        super(context, axe);
        this.eruption = eruption;
        this.spin = spin;
    }

    /**
     * Offhand key (F) while holding the Volcanic Axe:
     * normal = Eruption under the target you look at, crouching = spinning axe.
     */
    @Override
    protected void onOffhand(Player player, boolean sneaking) {
        if (sneaking) {
            useSpin(player);
        } else {
            useEruption(player);
        }
    }

    @Override
    protected void onPlayerDeath(Player player) {
        spin.stop(player);
    }

    @Override
    protected void onPlayerQuit(Player player) {
        spin.stop(player);
    }

    // ---------------------------------------------------------------- Abilities

    private void useEruption(Player player) {
        if (cooldowns.isOnCooldown(player, ERUPTION_ID)) return; // boss bar already shows the time left

        LivingEntity target = Aim.lookedAt(player, ERUPTION_RANGE, RAY_SIZE);
        if (target == null) {
            player.sendActionBar(Component.text("Look at a target to use Eruption", NamedTextColor.RED));
            return; // failed cast stays free (no cooldown)
        }

        cooldowns.start(player, ERUPTION_ID, "Eruption", BossBar.Color.RED, ERUPTION_COOLDOWN_MS);
        eruption.cast(player, EruptionEffect.groundBelow(target.getLocation()));
    }

    private void useSpin(Player player) {
        if (cooldowns.isOnCooldown(player, SPIN_ID)) return;

        cooldowns.start(player, SPIN_ID, "Spinning Axe", BossBar.Color.YELLOW, SPIN_COOLDOWN_MS);
        spin.start(player);
    }
}
