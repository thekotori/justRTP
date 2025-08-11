package eu.kotori.justRTP.handlers;

import eu.kotori.justRTP.JustRTP;
import eu.kotori.justRTP.handlers.hooks.HookManager;
import eu.kotori.justRTP.managers.ConfigManager;
import io.papermc.lib.PaperLib;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class RTPService {
    private enum WorldType { NORMAL, NETHER, THE_END }
    private enum FailureReason { BLACKLISTED_BLOCK, LAVA_NEARBY, LIQUID_FLOOR, AIR_FLOOR, OBSTRUCTED, INVALID_BIOME, REGION_CLAIM, UNKNOWN }
    private record SearchSummary(Map<FailureReason, Integer> failureCounts) {
        public SearchSummary() {
            this(new EnumMap<>(FailureReason.class));
        }
        public void increment(FailureReason reason) {
            failureCounts.merge(reason, 1, Integer::sum);
        }
        @Override
        public String toString() {
            if (failureCounts.isEmpty()) return "No failures.";
            return failureCounts.entrySet().stream()
                    .map(entry -> entry.getKey().name() + ": " + entry.getValue())
                    .collect(Collectors.joining(", "));
        }
    }

    private final JustRTP plugin;
    private final ConfigManager config;
    private final HookManager hookManager;
    private EnumSet<Material> blacklistedBlocks;
    private final Map<String, WorldType> worldTypes = new HashMap<>();

    private String worldMode;
    private Set<String> worldList;
    private String biomeMode;
    private Set<Biome> biomeList;

    public RTPService(JustRTP plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.hookManager = new HookManager(plugin);
        loadConfigValues();
    }

    public void loadConfigValues() {
        blacklistedBlocks = EnumSet.noneOf(Material.class);
        plugin.getConfig().getStringList("blacklist_blocks").forEach(name -> { try { blacklistedBlocks.add(Material.valueOf(name.toUpperCase())); } catch (IllegalArgumentException e) { plugin.getLogger().warning("Invalid blacklisted block: " + name); }});

        this.worldMode = plugin.getConfig().getString("rtp_settings.worlds.mode", "BLACKLIST").toUpperCase();
        this.worldList = new HashSet<>(plugin.getConfig().getStringList("rtp_settings.worlds.list"));

        worldTypes.clear();
        ConfigurationSection typesSection = plugin.getConfig().getConfigurationSection("world_types");
        if (typesSection != null) { typesSection.getKeys(false).forEach(worldName -> { try { worldTypes.put(worldName, WorldType.valueOf(typesSection.getString(worldName, "NORMAL").toUpperCase())); } catch (IllegalArgumentException e) { worldTypes.put(worldName, WorldType.NORMAL); }}); }

        this.biomeMode = plugin.getConfig().getString("rtp_settings.biomes.mode", "BLACKLIST").toUpperCase();
        this.biomeList = plugin.getConfig().getStringList("rtp_settings.biomes.list").stream()
                .map(str -> {
                    try {
                        return Biome.valueOf(str.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid biome name in config.yml: " + str);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public boolean isRtpEnabled(World world) {
        String worldName = world.getName();
        return "WHITELIST".equals(worldMode) ? worldList.contains(worldName) : !worldList.contains(worldName);
    }

    public void teleportPlayer(Player player, Location location) {
        if (!player.isOnline()) return;

        plugin.getFoliaScheduler().runAtEntity(player, () -> {
            player.setFallDistance(0f);
            PaperLib.teleportAsync(player, location).thenAccept(success -> {
                if (success) {
                    plugin.getFoliaScheduler().runAtEntity(player, () -> {
                        player.setFallDistance(0f);
                        plugin.getEffectsManager().applyPostTeleportEffects(player);
                    });
                }
            });
        });
    }

    public CompletableFuture<Optional<Location>> findSafeLocationForCache(World world) {
        boolean generateChunks = plugin.getConfigManager().shouldGenerateChunks(world);
        int attempts = plugin.getConfig().getInt("settings.attempts", 25);
        return findLocationAsync(null, world, attempts, Optional.empty(), Optional.empty(), generateChunks);
    }

    public CompletableFuture<Optional<Location>> findSafeLocation(Player player, World world, int attempts, Optional<Integer> minRadius, Optional<Integer> maxRadius) {
        boolean generateChunks = plugin.getConfigManager().shouldGenerateChunks(world);
        return findLocationAsync(player, world, attempts, minRadius, maxRadius, generateChunks);
    }

    private CompletableFuture<Optional<Location>> findLocationAsync(Player player, World world, int attemptsLeft, Optional<Integer> minRadius, Optional<Integer> maxRadius, boolean generateChunks) {
        SearchSummary summary = new SearchSummary();
        return findLocationRecursive(player, world, attemptsLeft, minRadius, maxRadius, generateChunks, summary);
    }

    private CompletableFuture<Optional<Location>> findLocationRecursive(Player player, World world, int attemptsLeft, Optional<Integer> minRadius, Optional<Integer> maxRadius, boolean generateChunks, SearchSummary summary) {
        if (attemptsLeft <= 0) {
            plugin.debug("findSafeLocation ran out of attempts for world " + world.getName() + ". Failure summary: " + summary);
            return CompletableFuture.completedFuture(Optional.empty());
        }

        WorldBorder border = world.getWorldBorder();
        double borderSize = border.getSize() / 2;
        Location borderCenter = border.getCenter();

        double initialMaxR = Math.min(borderSize, maxRadius.orElse(config.getInt(player, world, "max_radius", (int) borderSize)));
        double initialMinR = minRadius.orElse(config.getInt(player, world, "min_radius", 100));

        final double finalMinRadius = Math.min(initialMinR, initialMaxR);
        final double finalMaxRadius = Math.max(initialMinR, initialMaxR);

        ConfigurationSection worldConfig = plugin.getConfig().getConfigurationSection("custom_worlds." + world.getName());
        int cX = (worldConfig != null) ? worldConfig.getInt("center_x", 0) : (int) borderCenter.getX();
        int cZ = (worldConfig != null) ? worldConfig.getInt("center_z", 0) : (int) borderCenter.getZ();

        double angle = ThreadLocalRandom.current().nextDouble(2 * Math.PI);
        double radius = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * (finalMaxRadius - finalMinRadius) + finalMinRadius;

        final int x = (int) Math.max(borderCenter.getX() - borderSize, Math.min(borderCenter.getX() + borderSize, cX + radius * Math.cos(angle)));
        final int z = (int) Math.max(borderCenter.getZ() - borderSize, Math.min(borderCenter.getZ() + borderSize, cZ + radius * Math.sin(angle)));

        return PaperLib.getChunkAtAsync(world, x >> 4, z >> 4, generateChunks).thenCompose(chunk -> {
            if (chunk == null) {
                return findLocationRecursive(player, world, attemptsLeft - 1, minRadius, maxRadius, generateChunks, summary);
            }
            WorldType type = worldTypes.getOrDefault(world.getName(), WorldType.NORMAL);
            Optional<Location> safeSpot;
            switch(type) {
                case NETHER:
                    safeSpot = findSafeInNether(chunk, x, z, summary);
                    break;
                case THE_END:
                    safeSpot = findSafeInEnd(chunk, x, z, summary);
                    break;
                default:
                    safeSpot = findSafeInNormal(chunk, x, z, summary);
                    break;
            }

            if (safeSpot.isPresent()) {
                plugin.debug("Success: Found safe location at " + safeSpot.get().getBlockX() + "," + safeSpot.get().getBlockY() + "," + safeSpot.get().getBlockZ() + " after " + (plugin.getConfig().getInt("settings.attempts", 25) - attemptsLeft + 1) + " attempts.");
                return CompletableFuture.completedFuture(safeSpot);
            }
            return findLocationRecursive(player, world, attemptsLeft - 1, minRadius, maxRadius, generateChunks, summary);
        });
    }

    private Optional<Location> findSafeInNormal(Chunk chunk, int x, int z, SearchSummary summary) {
        int y = chunk.getWorld().getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= chunk.getWorld().getMinHeight()) return Optional.empty();
        Location loc = new Location(chunk.getWorld(), x, y, z);
        if (isSafe(loc, summary).isEmpty()) return Optional.of(loc.add(0.5, 1.5, 0.5));
        return Optional.empty();
    }

    private Optional<Location> findSafeInNether(Chunk chunk, int x, int z, SearchSummary summary) {
        for (int y = 120; y > chunk.getWorld().getMinHeight() + 5; y--) {
            Block groundBlock = chunk.getBlock(x & 15, y - 1, z & 15);
            Block feetBlock = chunk.getBlock(x & 15, y, z & 15);
            Block headBlock = chunk.getBlock(x & 15, y + 1, z & 15);

            if (groundBlock.getType().isSolid() && feetBlock.getType() == Material.AIR && headBlock.getType() == Material.AIR) {
                Location loc = groundBlock.getLocation();
                if (isSafe(loc, summary).isEmpty()) {
                    return Optional.of(feetBlock.getLocation().add(0.5, 0.5, 0.5));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Location> findSafeInEnd(Chunk chunk, int x, int z, SearchSummary summary) {
        for (int y = chunk.getWorld().getMaxHeight() -1; y > chunk.getWorld().getMinHeight(); y--) {
            Block groundBlock = chunk.getBlock(x & 15, y - 1, z & 15);
            if (groundBlock.getType().isSolid()) {
                Block feetBlock = chunk.getBlock(x & 15, y, z & 15);
                Block headBlock = chunk.getBlock(x & 15, y + 1, z & 15);
                if(feetBlock.getType().isAir() && headBlock.getType().isAir()) {
                    Location loc = groundBlock.getLocation();
                    if (isSafe(loc, summary).isEmpty()) {
                        return Optional.of(feetBlock.getLocation().add(0.5, 0.5, 0.5));
                    }
                }
            }
        }
        return Optional.empty();
    }


    public boolean isSafeForSpread(Location location) {
        Block floor = location.getBlock();
        if (blacklistedBlocks.contains(floor.getType())) return false;
        if (floor.isLiquid()) return false;
        if (floor.getType().isAir()) return false;
        Block feet = location.clone().add(0, 1, 0).getBlock();
        Block head = location.clone().add(0, 2, 0).getBlock();
        return feet.isPassable() && head.isPassable();
    }

    private Optional<FailureReason> isSafe(Location location, SearchSummary summary) {
        Block floor = location.getBlock();
        if (blacklistedBlocks.contains(floor.getType())) {
            summary.increment(FailureReason.BLACKLISTED_BLOCK);
            return Optional.of(FailureReason.BLACKLISTED_BLOCK);
        }
        if (location.getWorld().getEnvironment() != World.Environment.THE_END) {
            Block belowFloor = location.clone().subtract(0, 1, 0).getBlock();
            if (belowFloor.getType() == Material.LAVA) {
                summary.increment(FailureReason.LAVA_NEARBY);
                return Optional.of(FailureReason.LAVA_NEARBY);
            }
        }
        if (floor.isLiquid()) {
            summary.increment(FailureReason.LIQUID_FLOOR);
            return Optional.of(FailureReason.LIQUID_FLOOR);
        }
        if (floor.getType().isAir()) {
            summary.increment(FailureReason.AIR_FLOOR);
            return Optional.of(FailureReason.AIR_FLOOR);
        }

        Block feet = location.clone().add(0, 1, 0).getBlock();
        Block head = location.clone().add(0, 2, 0).getBlock();
        if (!feet.isPassable() || !head.isPassable()) {
            summary.increment(FailureReason.OBSTRUCTED);
            return Optional.of(FailureReason.OBSTRUCTED);
        }
        Biome biome = floor.getBiome();
        boolean biomeAllowed = "BLACKLIST".equals(biomeMode) ? !biomeList.contains(biome) : biomeList.contains(biome);
        if (!biomeAllowed) {
            summary.increment(FailureReason.INVALID_BIOME);
            return Optional.of(FailureReason.INVALID_BIOME);
        }
        if (plugin.getConfig().getBoolean("settings.respect_regions") && !hookManager.isLocationSafe(location)) {
            summary.increment(FailureReason.REGION_CLAIM);
            return Optional.of(FailureReason.REGION_CLAIM);
        }
        return Optional.empty();
    }
}