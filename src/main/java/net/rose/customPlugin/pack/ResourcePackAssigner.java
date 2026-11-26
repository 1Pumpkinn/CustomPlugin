package net.rose.customPlugin.pack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Sends the weapon texture pack to players when they join (and on /weaponpack).
 * The pack's SHA-1 comes from the config, or is worked out by downloading the zip once on startup/reload,
 * so clients can cache the pack and only re-download it when it changes.
 */
public final class ResourcePackAssigner implements Listener {

    private static final byte[] UNKNOWN_HASH = new byte[0];

    private final JavaPlugin plugin;

    private boolean enabled;
    private boolean required;
    private String url = "";
    private Component prompt = Component.empty();
    private long sendDelayTicks = 20L;
    private UUID packId = UUID.randomUUID();
    private CompletableFuture<byte[]> hash;

    public ResourcePackAssigner(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** (Re)reads the config and starts working out the pack hash. */
    public void load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        enabled = config.getBoolean("resource-pack.enabled", true);
        required = config.getBoolean("resource-pack.required", false);
        url = config.getString("resource-pack.url", "").trim();
        prompt = Component.text(config.getString("resource-pack.prompt", ""));
        sendDelayTicks = Math.max(0, config.getLong("resource-pack.send-delay-ticks", 20L));
        packId = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
        hash = null;

        if (!enabled) return;
        if (url.isEmpty()) {
            plugin.getLogger().warning("resource-pack.url is empty, so the weapon texture pack is not being sent.");
            enabled = false;
            return;
        }
        hash = resolveHash(config.getString("resource-pack.sha1", "").trim());
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Sends the pack to the player (after the hash is known). */
    public void send(Player player) {
        CompletableFuture<byte[]> pending = hash;
        if (!enabled || pending == null) return;

        pending.thenAccept(sha1 -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            boolean hashKnown = sha1.length > 0;
            // If the server couldn't verify the pack (bad link?), never kick people over it.
            player.setResourcePack(packId, url, hashKnown ? sha1 : null, prompt, required && hashKnown);
        }));
    }

    public void sendToAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            send(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> send(player), sendDelayTicks);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!event.getID().equals(packId)) return;
        Player player = event.getPlayer();

        switch (event.getStatus()) {
            case DECLINED -> player.sendMessage(Component.text(
                    "Weapons will look like plain items without the texture pack. Use /weaponpack to load it.",
                    NamedTextColor.YELLOW));
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> plugin.getLogger().warning(
                    "Resource pack " + event.getStatus() + " for " + player.getName() + " (url: " + url + ")");
            default -> { }
        }
    }

    // ---------------------------------------------------------------- Hash

    private CompletableFuture<byte[]> resolveHash(String configured) {
        if (configured.matches("(?i)[0-9a-f]{40}")) {
            return CompletableFuture.completedFuture(HexFormat.of().parseHex(configured));
        }
        if (!configured.isEmpty()) {
            plugin.getLogger().warning("resource-pack.sha1 is not 40 hex characters, so it is being worked out from the url instead.");
        }

        CompletableFuture<byte[]> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                future.complete(downloadHash(url));
                plugin.getLogger().info("Weapon texture pack hash worked out from " + url);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not download the weapon texture pack to hash it (" + url + "). "
                                + "It will be sent without a hash and is not enforced. Is the pack pushed / the url correct?", e);
                future.complete(UNKNOWN_HASH);
            }
        });
        return future;
    }

    private static byte[] downloadHash(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = response.body()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }
}
