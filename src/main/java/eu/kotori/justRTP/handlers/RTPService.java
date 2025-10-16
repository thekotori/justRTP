package eu.kotori.justRTP.handlers;

import eu.kotori.justRTP.JustRTP;
import eu.kotori.justRTP.handlers.hooks.HookManager;
import eu.kotori.justRTP.managers.ConfigManager;
import eu.kotori.justRTP.utils.SafetyValidator;
import io.papermc.lib.PaperLib;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
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
    private final Set<String> borderWarningShown = new HashSet<>(); 

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
        int attempts = getDimensionAttempts(world);
        return findLocationAsync(null, world, attempts, Optional.empty(), Optional.empty(), generateChunks);
    }

    public CompletableFuture<Optional<Location>> findSafeLocation(Player player, World world, int attempts, Optional<Integer> minRadius, Optional<Integer> maxRadius) {
        boolean generateChunks = plugin.getConfigManager().shouldGenerateChunks(world);
        int finalAttempts = (attempts > 0) ? attempts : getDimensionAttempts(world);
        return findLocationAsync(player, world, finalAttempts, minRadius, maxRadius, generateChunks);
    }
    
    private int getDimensionAttempts(World world) {
        World.Environment env = world.getEnvironment();
        if (env == World.Environment.NETHER) {
            return plugin.getConfig().getInt("settings.attempts_nether", 50);
        } else if (env == World.Environment.THE_END) {
            return plugin.getConfig().getInt("settings.attempts_end", 35);
        } else {
            return plugin.getConfig().getInt("settings.attempts", 25);
        }
    }

    private CompletableFuture<Optional<Location>> findLocationAsync(Player player, World world, int attemptsLeft, Optional<Integer> minRadius, Optional<Integer> maxRadius, boolean generateChunks) {
        SearchSummary summary = new SearchSummary();
        return findLocationRecursive(player, world, attemptsLeft, minRadius, maxRadius, generateChunks, summary);
    }

    private CompletableFuture<Optional<Location>> findLocationRecursive(Player player, World world, int attemptsLeft, Optional<Integer> minRadius, Optional<Integer> maxRadius, boolean generateChunks, SearchSummary summary) {
        if (attemptsLeft <= 0) {
            int totalAttempts = getDimensionAttempts(world);
            String worldType = world.getEnvironment().name();
            plugin.getLogger().warning("Failed to find safe location in " + world.getName() + " (" + worldType + ") after " + totalAttempts + " attempts.");
            plugin.getLogger().warning("Failure breakdown: " + summary);
            plugin.getLogger().warning("Check world configuration, chunk generation settings, and world_types in config.yml");
            return CompletableFuture.completedFuture(Optional.empty());
        }

        WorldBorder border = world.getWorldBorder();
        double borderSize = border.getSize() / 2;
        Location borderCenter = border.getCenter();

        final double ABSOLUTE_MAX_RADIUS = 10_000_000;
        final double SAFE_BORDER_SIZE = Math.min(borderSize, ABSOLUTE_MAX_RADIUS);
        
        if (borderSize > ABSOLUTE_MAX_RADIUS && !borderWarningShown.contains(world.getName())) {
            borderWarningShown.add(world.getName());
            plugin.getLogger().warning("World border for '" + world.getName() + "' is extremely large (" + borderSize + " blocks)!");
            plugin.getLogger().warning("Limiting RTP radius to " + ABSOLUTE_MAX_RADIUS + " blocks to prevent server crashes.");
        }

        double initialMaxR = Math.min(SAFE_BORDER_SIZE, maxRadius.orElse(config.getInt(player, world, "max_radius", (int) SAFE_BORDER_SIZE)));
        double initialMinR = minRadius.orElse(config.getInt(player, world, "min_radius", 100));

        final double finalMinRadius = Math.min(initialMinR, initialMaxR);
        final double finalMaxRadius = Math.max(initialMinR, initialMaxR);

        ConfigurationSection worldConfig = plugin.getConfig().getConfigurationSection("custom_worlds." + world.getName());
        int cX = (worldConfig != null) ? worldConfig.getInt("center_x", 0) : (int) borderCenter.getX();
        int cZ = (worldConfig != null) ? worldConfig.getInt("center_z", 0) : (int) borderCenter.getZ();

        double angle = ThreadLocalRandom.current().nextDouble(2 * Math.PI);
        double radius = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * (finalMaxRadius - finalMinRadius) + finalMinRadius;

        double targetX = cX + radius * Math.cos(angle);
        double targetZ = cZ + radius * Math.sin(angle);
        
        final int x = (int) Math.max(borderCenter.getX() - SAFE_BORDER_SIZE, Math.min(borderCenter.getX() + SAFE_BORDER_SIZE, targetX));
        final int z = (int) Math.max(borderCenter.getZ() - SAFE_BORDER_SIZE, Math.min(borderCenter.getZ() + SAFE_BORDER_SIZE, targetZ));
        
        final int MAX_COORDINATE = 10_000_000;
        if (Math.abs(x) > MAX_COORDINATE || Math.abs(z) > MAX_COORDINATE) {
            plugin.getLogger().severe("╔════════════════════════════════════════════════════════════╗");
            plugin.getLogger().severe("║  CRITICAL: Extreme coordinates detected!                  ║");
            plugin.getLogger().severe("║  X=" + x + ", Z=" + z + "                                  ║");
            plugin.getLogger().severe("║  This would cause chunk loading to freeze the server!     ║");
            plugin.getLogger().severe("║  Retrying with safer coordinates...                       ║");
            plugin.getLogger().severe("╚════════════════════════════════════════════════════════════╝");
            return findLocationRecursive(player, world, attemptsLeft - 1, minRadius, maxRadius, generateChunks, summary);
        }

        return PaperLib.getChunkAtAsync(world, x >> 4, z >> 4, generateChunks).thenCompose(chunk -> {
            if (chunk == null) {
                plugin.getLogger().warning("Failed to load chunk at " + (x >> 4) + ", " + (z >> 4) + " in " + world.getName() + " (generateChunks=" + generateChunks + ")");
                summary.increment(FailureReason.UNKNOWN);
                
                int totalAttempts = getDimensionAttempts(world);
                int attemptsMade = totalAttempts - attemptsLeft + 1;
                if (attemptsMade % 10 == 0) {
                    plugin.getLogger().warning("Chunk loading failures in " + world.getName() + " after " + attemptsMade + " attempts. Check world configuration!");
                }
                
                return findLocationRecursive(player, world, attemptsLeft - 1, minRadius, maxRadius, generateChunks, summary);
            }
            
            WorldType type = worldTypes.get(world.getName());
            if (type == null) {
                if (world.getEnvironment() == World.Environment.NETHER) {
                    type = WorldType.NETHER;
                    plugin.getLogger().info("[NETHER DETECTION] Auto-detected NETHER environment for world '" + world.getName() + "' - Y < 127 enforcement ACTIVE");
                } else if (world.getEnvironment() == World.Environment.THE_END) {
                    type = WorldType.THE_END;
                    plugin.debug("Auto-detected THE_END environment for " + world.getName());
                } else {
                    type = WorldType.NORMAL;
                }
            } else {
                plugin.getLogger().info("[WORLD TYPE] Using configured WorldType." + type + " for world '" + world.getName() + "'");
            }
            
            if (world.getEnvironment() == World.Environment.NETHER && type != WorldType.NETHER) {
                plugin.getLogger().warning("[NETHER OVERRIDE] World '" + world.getName() + "' has NETHER environment but type was " + type + " - FORCING to NETHER for safety!");
                type = WorldType.NETHER;
            }
            
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
                Location loc = safeSpot.get();
                
                if (!SafetyValidator.isLocationAbsolutelySafe(loc)) {
                    String reason = SafetyValidator.getUnsafeReason(loc);
                    plugin.getLogger().severe("╔════════════════════════════════════════════════════════════╗");
                    plugin.getLogger().severe("║  SAFETY VALIDATOR REJECTED LOCATION!                      ║");
                    plugin.getLogger().severe("║  World: " + world.getName() + " (" + world.getEnvironment() + ")    ║");
                    plugin.getLogger().severe("║  Location: " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "  ║");
                    plugin.getLogger().severe("║  Reason: " + reason + "                                   ║");
                    plugin.getLogger().severe("║  Retrying with different location...                      ║");
                    plugin.getLogger().severe("╚════════════════════════════════════════════════════════════╝");
                    summary.increment(FailureReason.UNKNOWN);
                    return findLocationRecursive(player, world, attemptsLeft - 1, minRadius, maxRadius, generateChunks, summary);
                }
                
                boolean isNetherWorld = (type == WorldType.NETHER) || (world.getEnvironment() == World.Environment.NETHER);
                
                if (isNetherWorld) {
                    double y = loc.getY();
                    if (y >= 126.0) {
                        plugin.getLogger().severe("╔════════════════════════════════════════════════════════════╗");
                        plugin.getLogger().severe("║  CRITICAL NETHER ROOF SPAWN PREVENTED!                    ║");
                        plugin.getLogger().severe("║  Location rejected: Y=" + y + " >= 126                    ║");
                        plugin.getLogger().severe("║  World: " + world.getName() + " (Type: " + type + ", Env: " + world.getEnvironment() + ") ║");
                        plugin.getLogger().severe("║  Head would be at: Y=" + (y + 1) + " (NETHER CEILING!)           ║");
                        plugin.getLogger().severe("║  Continuing search for safe location...                   ║");
                        plugin.getLogger().severe("╚════════════════════════════════════════════════════════════╝");
                        summary.increment(FailureReason.UNKNOWN);
                        return findLocationRecursive(player, world, attemptsLeft - 1, minRadius, maxRadius, generateChunks, summary);
                    }
                    plugin.getLogger().info("[NETHER SAFE] ✓ Nether location verified safe: Y=" + y + " (head at Y=" + (y + 1) + ") in " + world.getName());
                }
                
                if (world.getEnvironment() == World.Environment.THE_END) {
                    double y = loc.getY();
                    if (y < 10 || y > 120) {
                        plugin.getLogger().warning("[END SAFETY] Rejected Y=" + y + " (out of safe range 10-120)");
                        summary.increment(FailureReason.UNKNOWN);
                        return findLocationRecursive(player, world, attemptsLeft - 1, minRadius, maxRadius, generateChunks, summary);
                    }
                    plugin.debug("[END SAFE] ✓ End location verified safe: Y=" + y + " in " + world.getName());
                }
                
                if (world.getEnvironment() == World.Environment.NORMAL) {
                    double y = loc.getY();
                    if (y >= 127 || y < world.getMinHeight() + 5) {
                        plugin.getLogger().warning("[OVERWORLD SAFETY] Rejected Y=" + y + " (invalid height)");
                        summary.increment(FailureReason.UNKNOWN);
                        return findLocationRecursive(player, world, attemptsLeft - 1, minRadius, maxRadius, generateChunks, summary);
                    }
                    plugin.debug("[OVERWORLD SAFE] ✓ Overworld location verified safe: Y=" + y + " in " + world.getName());
                }
                
                plugin.debug("Success: Found safe location at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " after " + (getDimensionAttempts(world) - attemptsLeft + 1) + " attempts.");
                return CompletableFuture.completedFuture(safeSpot);
            }
            return findLocationRecursive(player, world, attemptsLeft - 1, minRadius, maxRadius, generateChunks, summary);
        });
    }

    private Optional<Location> findSafeInNormal(Chunk chunk, int x, int z, SearchSummary summary) {
        if (chunk.getWorld().getEnvironment() == World.Environment.NETHER) {
            plugin.getLogger().severe("╔══════════════════════════════════════════════════════════╗");
            plugin.getLogger().severe("║  CRITICAL BUG: findSafeInNormal() called for NETHER!   ║");
            plugin.getLogger().severe("║  World: " + chunk.getWorld().getName() + "                              ║");
            plugin.getLogger().severe("║  Redirecting to findSafeInNether() for safety!          ║");
            plugin.getLogger().severe("╚══════════════════════════════════════════════════════════╝");
            return findSafeInNether(chunk, x, z, summary);
        }
        
        int groundY = chunk.getWorld().getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (groundY <= chunk.getWorld().getMinHeight()) {
            plugin.debug("No valid ground found at " + x + ", " + z + " in " + chunk.getWorld().getName());
            return Optional.empty();
        }
        
        if (groundY >= 127) {
            plugin.getLogger().warning("[SAFETY] findSafeInNormal() found groundY=" + groundY + " >= 127 in " + 
                                     chunk.getWorld().getName() + " - rejecting for safety");
            summary.increment(FailureReason.UNKNOWN);
            return Optional.empty();
        }
        
        Location groundLoc = new Location(chunk.getWorld(), x, groundY, z);
        
        if (isSafe(groundLoc, summary).isEmpty()) {
            Location spawnLoc = new Location(chunk.getWorld(), x + 0.5, groundY + 1, z + 0.5);
            
            if (spawnLoc.getY() >= 127.0) {
                plugin.getLogger().severe("[CRITICAL SAFETY] findSafeInNormal() would spawn at Y=" + spawnLoc.getY() + 
                                        " >= 127! Rejecting to prevent nether ceiling spawn.");
                summary.increment(FailureReason.UNKNOWN);
                return Optional.empty();
            }
            
            plugin.debug("Found safe overworld spawn at Y=" + spawnLoc.getY() + " (ground at Y=" + groundY + ")");
            return Optional.of(spawnLoc);
        }
        return Optional.empty();
    }

    private Optional<Location> findSafeInNether(Chunk chunk, int x, int z, SearchSummary summary) {
        
        String worldName = chunk.getWorld().getName();
        plugin.getLogger().info("[NETHER SEARCH] Starting nether location search in '" + worldName + "' at X=" + x + " Z=" + z);
        
        int minHeight = Math.max(chunk.getWorld().getMinHeight(), 5); 
        int maxSearchY = 120; 
        int searchAttempts = 0;
        
        plugin.debug("[NETHER SEARCH] Search range: Y=" + maxSearchY + " down to Y=" + minHeight + " (total range: " + (maxSearchY - minHeight) + " blocks)");
        
        for (int y = maxSearchY; y > minHeight; y--) {
            searchAttempts++;
            
            if (y >= 126) { 
                plugin.getLogger().severe("CRITICAL: Nether search attempted Y=" + y + " >= 126! Skipping.");
                continue;
            }
            
            if ((y + 2) >= 127) {
                continue;
            }
            
            Block groundBlock = chunk.getBlock(x & 15, y - 1, z & 15);
            Block feetBlock = chunk.getBlock(x & 15, y, z & 15);
            Block headBlock = chunk.getBlock(x & 15, y + 1, z & 15);

            if (groundBlock.getType().isSolid() && feetBlock.getType() == Material.AIR && headBlock.getType() == Material.AIR) {
                Location loc = groundBlock.getLocation();
                Optional<FailureReason> safetyCheck = isSafe(loc, summary);
                if (safetyCheck.isEmpty()) {
                    Location safeLocation = feetBlock.getLocation().add(0.5, 0.5, 0.5);
                    
                    double finalY = safeLocation.getY();
                    if (finalY >= 126.0) {
                        plugin.getLogger().severe("CRITICAL BUG DETECTED: findSafeInNether generated Y=" + finalY + " >= 126!");
                        plugin.getLogger().severe("This location would put player head at Y=" + (finalY + 1) + " (NETHER CEILING!)");
                        plugin.getLogger().severe("Rejecting location and continuing search.");
                        continue;
                    }
                    
                    if (finalY + 1.0 >= 127.0) {
                        plugin.getLogger().severe("CRITICAL: Player head would be at Y=" + (finalY + 1.0) + " >= 127!");
                        continue;
                    }
                    
                    plugin.debug("Found safe nether location at Y=" + finalY + " (head at Y=" + (finalY + 1) + ") after " + searchAttempts + " attempts");
                    return Optional.of(safeLocation);
                }
            }
        }
        
        plugin.debug("No safe nether location found after " + searchAttempts + " Y-level checks (Y=" + maxSearchY + " down to Y=" + minHeight + ")");
        summary.increment(FailureReason.UNKNOWN);
        return Optional.empty();
    }

    private Optional<Location> findSafeInEnd(Chunk chunk, int x, int z, SearchSummary summary) {
        World world = chunk.getWorld();
        String worldName = world.getName();
        
        plugin.debug("[END SEARCH] Starting End location search in '" + worldName + "' at X=" + x + " Z=" + z);
        
        int maxSearchY = Math.min(120, world.getMaxHeight() - 1);
        int minSearchY = Math.max(10, world.getMinHeight() + 1);
        int searchAttempts = 0;
        
        plugin.debug("[END SEARCH] Search range: Y=" + maxSearchY + " down to Y=" + minSearchY);
        
        for (int y = maxSearchY; y > minSearchY; y--) {
            searchAttempts++;
            
            if (y < 10 || y > 120) {
                continue;
            }
            
            Block groundBlock = chunk.getBlock(x & 15, y - 1, z & 15);
            
            Material groundType = groundBlock.getType();
            if (!groundType.isSolid()) {
                continue;
            }
            
            if (groundType != Material.END_STONE && 
                groundType != Material.OBSIDIAN && 
                groundType != Material.END_STONE_BRICKS &&
                groundType != Material.PURPUR_BLOCK) {
                plugin.debug("[END SEARCH] Found non-standard ground type: " + groundType + " at Y=" + y);
            }
            
            Block feetBlock = chunk.getBlock(x & 15, y, z & 15);
            Block headBlock = chunk.getBlock(x & 15, y + 1, z & 15);
            
            if(feetBlock.getType().isAir() && headBlock.getType().isAir()) {
                Location loc = groundBlock.getLocation();
                Optional<FailureReason> safetyCheck = isSafe(loc, summary);
                
                if (safetyCheck.isEmpty()) {
                    Location safeLocation = feetBlock.getLocation().add(0.5, 0.5, 0.5);
                    
                    double finalY = safeLocation.getY();
                    if (finalY < 10.0 || finalY > 120.0) {
                        plugin.getLogger().warning("[END SAFETY] Generated unsafe Y=" + finalY + " - rejecting");
                        continue;
                    }
                    
                    boolean hasGroundBelow = false;
                    for (int checkY = y - 2; checkY > Math.max(0, y - 10); checkY--) {
                        Block checkBlock = chunk.getBlock(x & 15, checkY, z & 15);
                        if (checkBlock.getType().isSolid()) {
                            hasGroundBelow = true;
                            break;
                        }
                    }
                    
                    if (!hasGroundBelow) {
                        plugin.debug("[END SEARCH] No ground below Y=" + y + " - may be floating");
                        continue;
                    }
                    
                    plugin.debug("[END SEARCH] Found safe End location at Y=" + finalY + " (" + groundType + ") after " + searchAttempts + " attempts");
                    return Optional.of(safeLocation);
                }
            }
        }
        
        plugin.debug("[END SEARCH] No safe End location found after " + searchAttempts + " Y-level checks");
        summary.increment(FailureReason.UNKNOWN);
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