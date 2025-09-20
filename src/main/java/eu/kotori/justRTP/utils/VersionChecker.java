package eu.kotori.justRTP.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.kotori.justRTP.JustRTP;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

public class VersionChecker implements Listener {

    private final JustRTP plugin;
    private final String currentVersion;
    private final String apiUrl;

    public VersionChecker(JustRTP plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
        this.apiUrl = "https://api.kotori.ink/v1/version?product=justRTP";
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void check() {
        plugin.getFoliaScheduler().runAsync(() -> {
            try {
                URI uri = URI.create(this.apiUrl);
                HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "JustRTP Version Checker");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                        JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
                        String latestVersion = jsonObject.get("version").getAsString();

                        if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                            plugin.updateAvailable = true;
                            plugin.latestVersion = latestVersion;
                            StartupMessage.sendUpdateNotification(plugin);
                        } else {
                            plugin.updateAvailable = false;
                        }
                    }
                } else {
                    plugin.getLogger().warning("Version check failed with response code: " + responseCode);
                }
                connection.disconnect();

            } catch (Exception e) {
                plugin.getLogger().warning("Could not check for updates: " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("justrtp.admin") && plugin.updateAvailable) {
            plugin.getFoliaScheduler().runAtEntityLater(player, () -> {
                if(player.isOnline()) {
                    StartupMessage.sendUpdateNotification(player, plugin);
                }
            }, 60L);
        }
    }
}