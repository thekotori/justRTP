package eu.kotori.justRTP;

import eu.kotori.justRTP.addons.AddonManager;
import eu.kotori.justRTP.bstats.bukkit.Metrics;
import eu.kotori.justRTP.commands.RTPZoneCommand;
import eu.kotori.justRTP.commands.RTPZoneTabCompleter;
import eu.kotori.justRTP.handlers.PlayerListener;
import eu.kotori.justRTP.handlers.RTPService;
import eu.kotori.justRTP.handlers.WorldListener;
import eu.kotori.justRTP.handlers.hooks.VaultHook;
import eu.kotori.justRTP.managers.*;
import eu.kotori.justRTP.utils.*;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class JustRTP extends JavaPlugin {

    private static final int CONFIG_VERSION = 22;
    private static final int MESSAGES_CONFIG_VERSION = 14;
    private static final int MYSQL_CONFIG_VERSION = 5;
    private static final int ANIMATIONS_CONFIG_VERSION = 2;
    private static final int COMMANDS_CONFIG_VERSION = 4;
    private static final int ZONES_CONFIG_VERSION = 10;
    private static final int HOLOGRAMS_CONFIG_VERSION = 6;
    private static final int REDIS_CONFIG_VERSION = 3;

    private static JustRTP instance;
    private ConfigManager configManager;
    private CommandManager commandManager;
    private LocaleManager localeManager;
    private CooldownManager cooldownManager;
    private DelayManager delayManager;
    private RTPService rtpService;
    private TeleportQueueManager teleportQueueManager;
    private EffectsManager effectsManager;
    private FoliaScheduler foliaScheduler;
    private ProxyManager proxyManager;
    private DatabaseManager databaseManager;
    private LocationCacheManager locationCacheManager;
    private AnimationManager animationManager;
    private ConfirmationManager confirmationManager;
    private VaultHook vaultHook;
    private CrossServerManager crossServerManager;
    private RTPZoneManager rtpZoneManager;
    private ZoneSetupManager zoneSetupManager;
    private HologramManager hologramManager;
    private ZoneSyncManager zoneSyncManager;
    private VersionChecker versionChecker;
    private AddonManager addonManager;

    public boolean updateAvailable = false;
    public String latestVersion = "";

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveDefaultResource("messages.yml");
        saveDefaultResource("animations.yml");
        saveDefaultMysqlConfig();
        saveDefaultResource("commands.yml");
        saveDefaultResource("rtp_zones.yml");
        saveDefaultResource("holograms.yml");
        saveDefaultResource("display_entities.yml");
        saveDefaultResource("cache.yml");
        saveDefaultResource("redis.yml");

        ConfigUpdater.update(this, "config.yml", CONFIG_VERSION);
        ConfigUpdater.update(this, "messages.yml", MESSAGES_CONFIG_VERSION);
        ConfigUpdater.update(this, "mysql.yml", MYSQL_CONFIG_VERSION);
        ConfigUpdater.update(this, "animations.yml", ANIMATIONS_CONFIG_VERSION);
        ConfigUpdater.update(this, "commands.yml", COMMANDS_CONFIG_VERSION);
        ConfigUpdater.update(this, "rtp_zones.yml", ZONES_CONFIG_VERSION);
        ConfigUpdater.update(this, "holograms.yml", HOLOGRAMS_CONFIG_VERSION);
        ConfigUpdater.update(this, "redis.yml", REDIS_CONFIG_VERSION);

        configManager = new ConfigManager(this);
        commandManager = new CommandManager(this);
        foliaScheduler = new FoliaScheduler(this);

        if (configManager.isProxyMySqlEnabled()) {
            databaseManager = new DatabaseManager(this, foliaScheduler);
            if (!databaseManager.isConnected()) {
                getLogger().severe("Disabling proxy features due to failed MySQL connection.");
            }
        }

        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        vaultHook = new VaultHook(this);
        animationManager = new AnimationManager(this);
        localeManager = new LocaleManager(this);
        proxyManager = new ProxyManager(this);
        crossServerManager = new CrossServerManager(this);
        cooldownManager = new CooldownManager();
        rtpService = new RTPService(this);
        delayManager = new DelayManager(this);
        teleportQueueManager = new TeleportQueueManager(this);
        effectsManager = new EffectsManager(this);
        confirmationManager = new ConfirmationManager(this);
        zoneSetupManager = new ZoneSetupManager(this);
        hologramManager = new HologramManager(this);
        rtpZoneManager = new RTPZoneManager(this);
        zoneSyncManager = new ZoneSyncManager(this);
        addonManager = new AddonManager(this);

        locationCacheManager = new LocationCacheManager(this);

        commandManager.registerCommands();
        registerZoneCommands();
        playerListener = new PlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);

        versionChecker = new VersionChecker(this);
        foliaScheduler.runLater(() -> {
            hologramManager.initialize();
            
            if (!hologramManager.isUsingPacketEvents()) {
                getLogger().info("┌────────────────────────────────────────────────────────────┐");
                getLogger().info("│  💡 RECOMMENDATION: Install PacketEvents                   │");
                getLogger().info("│                                                            │");
                getLogger().info("│  PacketEvents provides high-performance packet-based       │");
                getLogger().info("│  holograms that are more efficient and have better         │");
                getLogger().info("│  compatibility with Folia multi-threaded regions.          │");
                getLogger().info("│                                                            │");
                getLogger().info("│  Download: https://modrinth.com/plugin/packetevents       │");
                getLogger().info("│  Current: Using entity-based holograms (Display entities) │");
                getLogger().info("└────────────────────────────────────────────────────────────┘");
            }
            
            hologramManager.cleanupAllHolograms();
            rtpZoneManager.loadZones();
            locationCacheManager.initialize();
            zoneSyncManager.initialize();
            addonManager.loadAddons();
            StartupMessage.sendStartupMessage(this);
            versionChecker.check();

            for (Player player : getServer().getOnlinePlayers()) {
                rtpZoneManager.handlePlayerMove(player, player.getLocation());
            }
            
            startServerWorldsHeartbeat();
        }, 1L);

        if (getConfig().getBoolean("bstats.enabled", true)) {
            int pluginId = 26850;
            new Metrics(this, pluginId);
            getLogger().info("bStats metrics enabled. Thank you for your support!");
        } else {
            getLogger().info("bStats metrics are disabled in the config.");
        }
    }

    @Override
    public void onDisable() {
        if(addonManager != null) addonManager.disableAddons();
        if(rtpZoneManager != null) rtpZoneManager.shutdownAllTasks();
        if(effectsManager != null) effectsManager.removeAllBossBars();
        if (databaseManager != null && databaseManager.isConnected()) {
            databaseManager.close();
        }
        if (locationCacheManager != null) {
            locationCacheManager.shutdown();
        }
        if (hologramManager != null) {
            hologramManager.cleanupAllHolograms();
        }
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    public void reload() {
        if(rtpZoneManager != null) rtpZoneManager.shutdownAllTasks();
        if(hologramManager != null) hologramManager.cleanupAllHolograms();

        ConfigUpdater.update(this, "config.yml", CONFIG_VERSION);
        ConfigUpdater.update(this, "messages.yml", MESSAGES_CONFIG_VERSION);
        ConfigUpdater.update(this, "mysql.yml", MYSQL_CONFIG_VERSION);
        ConfigUpdater.update(this, "animations.yml", ANIMATIONS_CONFIG_VERSION);
        ConfigUpdater.update(this, "commands.yml", COMMANDS_CONFIG_VERSION);
        ConfigUpdater.update(this, "rtp_zones.yml", ZONES_CONFIG_VERSION);
        ConfigUpdater.update(this, "holograms.yml", HOLOGRAMS_CONFIG_VERSION);

        reloadConfig();
        configManager.reload();

        localeManager.loadMessages();
        rtpService.loadConfigValues();
        teleportQueueManager.reload();
        commandManager.registerCommands();
        hologramManager.initialize();
        rtpZoneManager.loadZones();
        vaultHook.setupEconomy();
        crossServerManager.reload();

        if (databaseManager != null && databaseManager.isConnected()) {
            databaseManager.close();
        }
        if (configManager.isProxyMySqlEnabled()) {
            databaseManager = new DatabaseManager(this, foliaScheduler);
            if (!databaseManager.isConnected()) {
                getLogger().severe("Disabling proxy features due to failed MySQL connection on reload.");
            }
        } else {
            databaseManager = null;
        }

        if (locationCacheManager != null) {
            locationCacheManager.shutdown();
        }
        locationCacheManager = new LocationCacheManager(this);
        locationCacheManager.initialize();
        animationManager = new AnimationManager(this);

        for (Player player : getServer().getOnlinePlayers()) {
            rtpZoneManager.handlePlayerMove(player, player.getLocation());
        }

        getLogger().info("JustRTP configuration reloaded and updated.");
    }

    private void registerZoneCommands() {
        PluginCommand rtpZoneCmd = getCommand("rtpzone");
        if (rtpZoneCmd != null) {
            rtpZoneCmd.setExecutor(new RTPZoneCommand(this));
            rtpZoneCmd.setTabCompleter(new RTPZoneTabCompleter(this));
        }
    }

    private void saveDefaultMysqlConfig() {
        File mysqlFile = new File(getDataFolder(), "mysql.yml");
        if (!mysqlFile.exists()) {
            saveResource("mysql.yml", false);
        }
    }

    private void saveDefaultResource(String resourcePath) {
        File resourceFile = new File(getDataFolder(), resourcePath);
        if (!resourceFile.exists()) {
            saveResource(resourcePath, false);
        }
    }

    public void debug(String message) {
        if (configManager != null && configManager.isDebugMode()) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public static JustRTP getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public LocaleManager getLocaleManager() { return localeManager; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public DelayManager getDelayManager() { return delayManager; }
    public RTPService getRtpService() { return rtpService; }
    public TeleportQueueManager getTeleportQueueManager() { return teleportQueueManager; }
    public EffectsManager getEffectsManager() { return effectsManager; }
    public FoliaScheduler getFoliaScheduler() { return foliaScheduler; }
    public ProxyManager getProxyManager() { return proxyManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public LocationCacheManager getLocationCacheManager() { return locationCacheManager; }
    public AnimationManager getAnimationManager() { return animationManager; }
    public CommandManager getCommandManager() { return commandManager; }
    public ConfirmationManager getConfirmationManager() { return confirmationManager; }
    public VaultHook getVaultHook() { return vaultHook; }
    public CrossServerManager getCrossServerManager() { return crossServerManager; }
    public RTPZoneManager getRtpZoneManager() { return rtpZoneManager; }
    public ZoneSetupManager getZoneSetupManager() { return zoneSetupManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public ZoneSyncManager getZoneSyncManager() { return zoneSyncManager; }
    public VersionChecker getVersionChecker() { return versionChecker; }
    public AddonManager getAddonManager() { return addonManager; }
    
    private DataManager dataManager;
    public DataManager getDataManager() { 
        if (dataManager == null) {
            dataManager = new DataManager(this);
        }
        return dataManager; 
    }
    
    private PlayerListener playerListener;
    public PlayerListener getPlayerListener() { 
        return playerListener;
    }
    
    public void setPlayerListener(PlayerListener listener) {
        this.playerListener = listener;
    }
    
    private void startServerWorldsHeartbeat() {
        if (!configManager.isProxyMySqlEnabled() || databaseManager == null || !databaseManager.isConnected()) {
            debug("Server worlds heartbeat disabled (MySQL not configured)");
            return;
        }
        
        String thisServer = configManager.getProxyThisServerName();
        if (thisServer == null || thisServer.isEmpty() || thisServer.equals("server-name")) {
            getLogger().warning("Cannot start server worlds heartbeat: 'this_server_name' not configured");
            return;
        }
        
        updateServerWorldsList();
        
        foliaScheduler.runTimer(() -> updateServerWorldsList(), 2400L, 2400L);
        
        debug("Server worlds heartbeat started for server: " + thisServer);
    }
    
    private void updateServerWorldsList() {
        String thisServer = configManager.getProxyThisServerName();
        List<World> worlds = new ArrayList<>(getServer().getWorlds());
        
        databaseManager.updateServerWorlds(thisServer, worlds)
            .exceptionally(ex -> {
                debug("Failed to update server worlds: " + ex.getMessage());
                return null;
            });
    }
}