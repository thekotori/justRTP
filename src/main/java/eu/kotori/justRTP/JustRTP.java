package eu.kotori.justRTP;

import eu.kotori.justRTP.bstats.bukkit.Metrics;
import eu.kotori.justRTP.commands.RTPZoneCommand;
import eu.kotori.justRTP.commands.RTPZoneTabCompleter;
import eu.kotori.justRTP.handlers.PlayerListener;
import eu.kotori.justRTP.handlers.RTPService;
import eu.kotori.justRTP.handlers.WorldListener;
import eu.kotori.justRTP.handlers.hooks.VaultHook;
import eu.kotori.justRTP.managers.*;
import eu.kotori.justRTP.utils.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class JustRTP extends JavaPlugin {

    private static final int CONFIG_VERSION = 15;
    private static final int MESSAGES_CONFIG_VERSION = 9;
    private static final int MYSQL_CONFIG_VERSION = 2;
    private static final int ANIMATIONS_CONFIG_VERSION = 1;
    private static final int COMMANDS_CONFIG_VERSION = 1;
    private static final int ZONES_CONFIG_VERSION = 6;
    private static final int HOLOGRAMS_CONFIG_VERSION = 3;

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
    private VersionChecker versionChecker;

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

        ConfigUpdater.update(this, "config.yml", CONFIG_VERSION);
        ConfigUpdater.update(this, "messages.yml", MESSAGES_CONFIG_VERSION);
        ConfigUpdater.update(this, "mysql.yml", MYSQL_CONFIG_VERSION);
        ConfigUpdater.update(this, "animations.yml", ANIMATIONS_CONFIG_VERSION);
        ConfigUpdater.update(this, "commands.yml", COMMANDS_CONFIG_VERSION);
        ConfigUpdater.update(this, "rtp_zones.yml", ZONES_CONFIG_VERSION);
        ConfigUpdater.update(this, "holograms.yml", HOLOGRAMS_CONFIG_VERSION);

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
        locationCacheManager = new LocationCacheManager(this);
        zoneSetupManager = new ZoneSetupManager(this);
        hologramManager = new HologramManager(this);
        rtpZoneManager = new RTPZoneManager(this);

        commandManager.registerCommands();
        registerZoneCommands();
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);

        versionChecker = new VersionChecker(this);

        foliaScheduler.runLater(() -> {
            hologramManager.initialize();
            hologramManager.cleanupAllHolograms();
            rtpZoneManager.loadZones();
            locationCacheManager.initialize();
            StartupMessage.sendStartupMessage(this);
            versionChecker.check();

            for (Player player : getServer().getOnlinePlayers()) {
                rtpZoneManager.handlePlayerMove(player, player.getLocation());
            }
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
    public VersionChecker getVersionChecker() { return versionChecker; }
}