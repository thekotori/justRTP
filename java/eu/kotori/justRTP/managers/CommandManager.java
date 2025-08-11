package eu.kotori.justRTP.managers;

import eu.kotori.justRTP.JustRTP;
import eu.kotori.justRTP.commands.RTPCommand;
import eu.kotori.justRTP.commands.RTPTabCompleter;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class CommandManager {

    private final JustRTP plugin;
    private FileConfiguration cmdConfig;
    private final NamespacedKey wandKey;

    public CommandManager(JustRTP plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "rtp_zone_wand");
        loadCommandConfig();
    }

    private void loadCommandConfig() {
        File cmdFile = new File(plugin.getDataFolder(), "commands.yml");
        if (!cmdFile.exists()) {
            plugin.saveResource("commands.yml", false);
        }
        cmdConfig = YamlConfiguration.loadConfiguration(cmdFile);
    }

    public boolean isAliasEnabled(String alias) {
        String cleanAlias = alias;
        int colonIndex = alias.indexOf(':');
        if (colonIndex >= 0) {
            cleanAlias = alias.substring(colonIndex + 1);
        }

        if (cleanAlias.equalsIgnoreCase("justrtp")) {
            return true;
        }
        return cmdConfig.getBoolean("enabled-aliases." + cleanAlias.toLowerCase(), false);
    }

    public void registerCommands() {
        loadCommandConfig();

        RTPCommand rtpExecutor = new RTPCommand(plugin);
        RTPTabCompleter rtpCompleter = new RTPTabCompleter(plugin);

        PluginCommand mainCommand = plugin.getCommand("justrtp");
        if (mainCommand != null) {
            mainCommand.setExecutor(rtpExecutor);
            mainCommand.setTabCompleter(rtpCompleter);
        }
    }

    public NamespacedKey getWandKey() {
        return wandKey;
    }
}