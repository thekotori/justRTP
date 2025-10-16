package eu.kotori.justRTP.managers;
import eu.kotori.justRTP.JustRTP;
import eu.kotori.justRTP.utils.task.CancellableTask;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
public class LocationCacheManager {
    private final JustRTP plugin;
    private final Map<String, ConcurrentLinkedQueue<Location>> locationCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> isRefilling = new ConcurrentHashMap<>();
    private final Map<String, Long> failedWorldsCooldown = new ConcurrentHashMap<>();
    private CancellableTask refillTask;
    private boolean cacheEnabled;
    private int cacheSize;
    private final File cacheFile;
    private FileConfiguration cacheConfig;
    private static final long COOLDOWN_PERIOD = 60000;

    public LocationCacheManager(JustRTP plugin) {
        this.plugin = plugin;
        this.cacheFile = new File(plugin.getDataFolder(), "cache.yml");
    }

    public void initialize() {
        this.cacheEnabled = plugin.getConfig().getBoolean("location_cache.enabled", true);
        if (!cacheEnabled) {
            plugin.debug("Location cache is disabled.");
            return;
        }

        loadCacheFromFile();

        this.cacheSize = plugin.getConfig().getInt("location_cache.cache_size", 20);
        long interval = plugin.getConfig().getLong("location_cache.refill_interval_seconds", 5) * 20L;

        isRefilling.clear();
        failedWorldsCooldown.clear();

        ConfigurationSection cacheWorldsSection = plugin.getConfig().getConfigurationSection("location_cache.worlds");
        if (cacheWorldsSection != null) {
            for (String worldName : cacheWorldsSection.getKeys(false)) {
                World world = plugin.getServer().getWorld(worldName);
                if (world != null && plugin.getRtpService().isRtpEnabled(world)) {
                    locationCache.putIfAbsent(worldName, new ConcurrentLinkedQueue<>());
                    isRefilling.put(worldName, false);
                    plugin.debug("Initializing location cache for world: " + worldName + ". Found " + locationCache.get(worldName).size() + " cached locations.");
                } else {
                    plugin.getLogger().warning("World '" + worldName + "' listed in location_cache.worlds is not loaded or RTP is disabled for it.");
                }
            }
        }


        startRefillTask(interval);
        plugin.getLogger().info("Location Cache initialized for " + locationCache.size() + " worlds. Target size per world: " + cacheSize);
    }

    public void shutdown() {
        if (refillTask != null) {
            refillTask.cancel();
        }
        if (cacheEnabled) {
            saveCacheToFile();
        }
    }

    private void loadCacheFromFile() {
        if (!cacheFile.exists()) {
            plugin.saveResource("cache.yml", false);
        }
        cacheConfig = YamlConfiguration.loadConfiguration(cacheFile);
        ConfigurationSection cacheSection = cacheConfig.getConfigurationSection("cache");
        if (cacheSection != null) {
            for (String worldName : cacheSection.getKeys(false)) {
                @SuppressWarnings("unchecked")
                List<Location> locations = (List<Location>) cacheSection.getList(worldName, new ArrayList<>());
                locationCache.put(worldName, new ConcurrentLinkedQueue<>(locations));
            }
        }

        cacheConfig.set("cache", null);
        try {
            cacheConfig.save(cacheFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not clear cache.yml after loading.", e);
        }
    }

    private void saveCacheToFile() {
        ConfigurationSection cacheSection = cacheConfig.createSection("cache");
        for (Map.Entry<String, ConcurrentLinkedQueue<Location>> entry : locationCache.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                cacheSection.set(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        try {
            cacheConfig.save(cacheFile);
            plugin.getLogger().info("Saved " + locationCache.values().stream().mapToInt(Queue::size).sum() + " locations to cache.yml.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save location cache to file.", e);
        }
    }

    private void startRefillTask(long interval) {
        refillTask = plugin.getFoliaScheduler().runTimer(() -> {
            for (String worldName : locationCache.keySet()) {
                World world = plugin.getServer().getWorld(worldName);
                if (world != null) {
                    refillCache(world);
                }
            }
        }, 100L, interval);
    }

    private void refillCache(World world) {
        if (!plugin.getConfigManager().isCacheEnabledForWorld(world)) return;

        ConcurrentLinkedQueue<Location> queue = locationCache.get(world.getName());
        if (queue == null || queue.size() >= cacheSize) {
            return;
        }

        long lastFailure = failedWorldsCooldown.getOrDefault(world.getName(), 0L);
        if (System.currentTimeMillis() - lastFailure < COOLDOWN_PERIOD) {
            return;
        }

        if (isRefilling.replace(world.getName(), false, true)) {
            plugin.debug("Starting refill worker for world '" + world.getName() + "'. Current size: " + queue.size() + "/" + cacheSize);
            fillQueueWorker(world, cacheSize - queue.size());
        }
    }

    private void fillQueueWorker(World world, int locationsNeeded) {
        if (locationsNeeded <= 0) {
            isRefilling.put(world.getName(), false);
            plugin.debug("Cache for world '" + world.getName() + "' is now full. Stopping worker.");
            return;
        }

        plugin.getRtpService().findSafeLocationForCache(world)
                .whenCompleteAsync((locationOpt, throwable) -> {
                    try {
                        if (throwable != null) {
                            plugin.getLogger().warning("Exception during location search for '" + world.getName() + "' cache: " + throwable.getMessage());
                            failedWorldsCooldown.put(world.getName(), System.currentTimeMillis());
                            return;
                        }
                        
                        if (locationOpt.isPresent()) {
                            ConcurrentLinkedQueue<Location> queue = locationCache.get(world.getName());
                            if (queue != null) {
                                queue.add(locationOpt.get());
                            }
                            failedWorldsCooldown.remove(world.getName());
                            plugin.getFoliaScheduler().runAsync(() -> fillQueueWorker(world, locationsNeeded - 1));
                        } else {
                            plugin.getLogger().warning("Failed to find a safe location for '" + world.getName() + "' cache after many attempts. Pausing searches for this world for 1 minute.");
                            failedWorldsCooldown.put(world.getName(), System.currentTimeMillis());
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Unexpected error in fillQueueWorker for '" + world.getName() + "': " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        if (throwable != null || !locationOpt.isPresent() || locationsNeeded <= 1) {
                            isRefilling.put(world.getName(), false);
                        }
                    }
                });
    }

    public Optional<Location> getLocation(World world) {
        if (world == null) {
            plugin.getLogger().log(Level.WARNING, "Attempted to get a cached location for a null world.", new Throwable());
            return Optional.empty();
        }
        if (!cacheEnabled) {
            return Optional.empty();
        }
        return Optional.ofNullable(locationCache.getOrDefault(world.getName(), new ConcurrentLinkedQueue<>()).poll());
    }
}