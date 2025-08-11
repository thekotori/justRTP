package eu.kotori.justRTP.utils;

import eu.kotori.justRTP.JustRTP;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ConfigUpdater {

    public static void update(JustRTP plugin, String fileName, int latestVersion) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        if (!configFile.exists()) {
            return;
        }

        FileConfiguration userConfig = YamlConfiguration.loadConfiguration(configFile);

        if (!userConfig.contains("config-version") || userConfig.getInt("config-version") < latestVersion) {
            int currentVersion = userConfig.getInt("config-version", 0);
            plugin.getLogger().info("Your " + fileName + " is outdated! Updating from version " + currentVersion + " to " + latestVersion + "...");

            File backupFile = new File(plugin.getDataFolder(), fileName + ".v" + currentVersion + ".old");
            try {
                Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create backup for " + fileName + "! Aborting update.");
                e.printStackTrace();
                return;
            }

            try (InputStream defaultStream = plugin.getResource(fileName)) {
                if (defaultStream == null) {
                    plugin.getLogger().severe("Could not find default " + fileName + " in JAR! Aborting update.");
                    return;
                }
                FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));

                for (String key : defaultConfig.getKeys(true)) {
                    if (userConfig.contains(key)) {
                        defaultConfig.set(key, userConfig.get(key));
                    }
                }

                defaultConfig.set("config-version", latestVersion);
                defaultConfig.save(configFile);

                plugin.getLogger().info(fileName + " has been successfully updated. Your old file was saved as " + backupFile.getName());
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save the updated " + fileName + "!");
                e.printStackTrace();
            }
        }
    }
}