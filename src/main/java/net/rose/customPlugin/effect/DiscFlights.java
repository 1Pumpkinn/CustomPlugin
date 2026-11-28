package net.rose.customPlugin.effect;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** The discs (or hats) currently in the air, one per player: the bookkeeping the Zeus Disc and the Hat share. */
public final class DiscFlights {

    private final Map<UUID, ZeusDiscFlight> flights = new HashMap<>();

    public boolean isFlying(Player player) {
        return flights.containsKey(player.getUniqueId());
    }

    /**
     * Starts a flight for the player and tracks it until it ends. The launcher gets the callback to pass on as
     * the flight's end handler. Whatever it throws is passed on, with nothing tracked.
     */
    public void launch(Player player, Function<Consumer<ZeusDiscFlight>, ZeusDiscFlight> launcher) {
        UUID id = player.getUniqueId();
        flights.put(id, launcher.apply(flight -> flights.remove(id, flight)));
    }

    /** Brings the player's disc home now (they are leaving, and a disc in the air would be lost). */
    public void end(Player player) {
        ZeusDiscFlight flight = flights.get(player.getUniqueId());
        if (flight != null) flight.end();
    }

    /** Brings every disc home, for the plugin shutting down. */
    public void endAll() {
        for (ZeusDiscFlight flight : new ArrayList<>(flights.values())) {
            flight.end();
        }
    }
}
