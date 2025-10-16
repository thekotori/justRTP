package eu.kotori.justRTP.managers;

import eu.kotori.justRTP.JustRTP;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public class ConfigManager {
    private final JustRTP plugin;
    private final Map<String, String> worldAliases = new HashMap<>();

    public ConfigManager(JustRTP plugin) {
        this.plugin = plugin;
        loadWorldAliases();
    }

    public void reload() {
        plugin.reloadConfig();
        loadWorldAliases();
    }

    private void loadWorldAliases() {
        worldAliases.clear();
        ConfigurationSection aliasSection = plugin.getConfig().getConfigurationSection("rtp_settings.world_aliases");
        if (aliasSection != null) {
            for (String alias : aliasSection.getKeys(false)) {
                worldAliases.put(alias.toLowerCase(), aliasSection.getString(alias));
            }
        }
    }

    public String resolveWorldAlias(String input) {
        if (input == null) return null;
        return worldAliases.getOrDefault(input.toLowerCase(), input);
    }

    public Map<String, String> getWorldAliases() {
        return Collections.unmodifiableMap(worldAliases);
    }

    private Optional<String> getHighestPriorityGroup(Player player) {
        if (player == null) {
            return Optional.empty();
        }

        ConfigurationSection groupsSection = plugin.getConfig().getConfigurationSection("permission_groups.groups");
        if (!plugin.getConfig().getBoolean("permission_groups.enabled") || groupsSection == null) {
            return Optional.empty();
        }
        return groupsSection.getKeys(false).stream()
                .filter(groupName -> player.hasPermission("justrtp.group." + groupName))
                .min(Comparator.comparingInt(groupName -> groupsSection.getInt(groupName + ".priority", Integer.MAX_VALUE)));
    }

    public int getInt(Player player, World world, String path, int def) {
        Optional<String> groupOpt = getHighestPriorityGroup(player);
        if (groupOpt.isPresent()) {
            String group = groupOpt.get();
            String groupPath = "permission_groups.groups." + group + ".worlds." + world.getName() + "." + path;
            if (plugin.getConfig().contains(groupPath)) {
                return plugin.getConfig().getInt(groupPath);
            }
        }
        String customWorldPath = "custom_worlds." + world.getName() + "." + path;
        String generalPath = "settings." + path;
        return plugin.getConfig().getInt(customWorldPath, plugin.getConfig().getInt(generalPath, def));
    }


    public double getDouble(Player player, World world, String path, double def) {
        Optional<String> groupOpt = getHighestPriorityGroup(player);
        if (groupOpt.isPresent()) {
            String group = groupOpt.get();
            String groupPath = "permission_groups.groups." + group + ".worlds." + world.getName() + "." + path;
            if (plugin.getConfig().contains(groupPath)) {
                return plugin.getConfig().getDouble(groupPath);
            }
        }
        String customWorldPath = "custom_worlds." + world.getName() + "." + path;
        String generalPath = "settings." + path;
        String economyPath = "economy." + path;

        if (plugin.getConfig().contains(customWorldPath)) {
            return plugin.getConfig().getDouble(customWorldPath);
        }
        if (plugin.getConfig().contains(generalPath)) {
            return plugin.getConfig().getDouble(generalPath);
        }
        return plugin.getConfig().getDouble(economyPath, def);
    }


    public int getCooldown(Player player, World world) {
        if (player.hasPermission("justrtp.cooldown.bypass")) return 0;
        return getInt(player, world, "cooldown", 30);
    }

    public int getDelay(Player player, World world) {
        if (player.hasPermission("justrtp.delay.bypass")) return 0;
        return getInt(player, world, "delay", 3);
    }

    public double getEconomyCost(Player player, World world) {
        if (player.hasPermission("justrtp.cost.bypass")) return 0.0;
        return getDouble(player, world, "cost", 100.0);
    }

    public boolean getProxyEnabled() {
        return plugin.getConfig().getBoolean("proxy.enabled", false);
    }

    public String getProxyThisServerName() {
        return plugin.getConfig().getString("proxy.this_server_name", "");
    }

    public String getProxyServerAlias(String serverName) {
        return plugin.getConfig().getString("proxy.aliases." + serverName, serverName);
    }

    public boolean getCrossServerNoDelay() {
        return plugin.getConfig().getBoolean("proxy.cross_server_rtp_no_delay", true);
    }

    public List<String> getProxyServers() {
        return plugin.getConfig().getStringList("proxy.servers");
    }

    public boolean isProxyMySqlEnabled() {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "mysql.yml")).getBoolean("enabled", false);
    }

    public boolean isDebugMode() {
        return plugin.getConfig().getBoolean("settings.debug", false);
    }

    public boolean isCacheEnabledForWorld(World world) {
        return plugin.getConfig().isConfigurationSection("location_cache.worlds." + world.getName());
    }

    public boolean shouldGenerateChunks(World world) {
        return plugin.getConfig().getBoolean("location_cache.worlds." + world.getName() + ".generate_chunks", false);
    }

    public boolean isZoneSyncEnabled() {
        return plugin.getConfig().getBoolean("proxy.zone_sync.enabled", false);
    }

    public boolean isRedisPubSubEnabled() {
        return plugin.getConfig().getBoolean("redis.performance.enable-pubsub", false);
    }

    public int getZoneSyncInterval() {
        return plugin.getConfig().getInt("proxy.zone_sync.sync_interval", 10);
    }

    public String getZoneSyncMode() {
        return plugin.getConfig().getString("proxy.zone_sync.mode", "PULL");
    }

    public boolean isZoneSyncAutoReload() {
        return plugin.getConfig().getBoolean("proxy.zone_sync.auto_reload", true);
    }

    public boolean isZoneSyncNotifyEnabled() {
        return plugin.getConfig().getBoolean("proxy.zone_sync.notify_sync", true);
    }

    public String getZoneSyncStorage() {
        return plugin.getConfig().getString("proxy.zone_sync.storage", "BOTH");
    }

    public boolean isRedisEnabled() {
        return plugin.getConfig().getBoolean("redis.enabled", false);
    }
}