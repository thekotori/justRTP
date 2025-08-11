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
import java.net.URL;

public class VersionChecker implements Listener {

    private final JustRTP plugin;
    private final String currentVersion;
    private final String apiUrl;

    public VersionChecker(JustRTP plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        this.apiUrl = "https://api.kotori.club/v1/version?product=justRTP";
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void check() {
        plugin.getFoliaScheduler().runAsync(() -> {
            try {
                URL url = new URL(this.apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
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
                        }
                    }
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