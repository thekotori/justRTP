package eu.kotori.justRTP.utils;

import eu.kotori.justRTP.JustRTP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.concurrent.CompletableFuture;

public class SafetyValidator {
    public static boolean isLocationAbsolutelySafe(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        
        World world = location.getWorld();
        World.Environment env = world.getEnvironment();
        
        switch (env) {
            case NETHER:
                return isNetherLocationSafe(location);
            case THE_END:
                return isEndLocationSafe(location);
            case NORMAL:
            default:
                return isOverworldLocationSafe(location);
        }
    }
    
    private static boolean isNetherLocationSafe(Location location) {
        World world = location.getWorld();
        double y = location.getY();
        if (y >= 126.0) {
            return false;
        }
        
        double headY = y + 1.0;
        if (headY >= 127.0) {
            return false;
        }
        
        if (y < 5) {
            return false;
        }
        
        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();
        Block groundBlock = world.getBlockAt(blockX, blockY - 1, blockZ);
        Block feetBlock = world.getBlockAt(blockX, blockY, blockZ);
        Block headBlock = world.getBlockAt(blockX, blockY + 1, blockZ);
        if (!groundBlock.getType().isSolid()) {
            return false;
        }
        
        if (isDangerousBlock(groundBlock.getType())) {
            return false;
        }
        
        if (feetBlock.getType().isSolid() || headBlock.getType().isSolid()) {
            return false;
        }
        
        if (hasLavaNearby(location)) {
            return false;
        }
        
        Block ceilingBlock = world.getBlockAt(blockX, 127, blockZ);
        if (ceilingBlock.getType() == Material.BEDROCK) {
            if (y >= 120) {
                return false;
            }
        }
        
        return true;
    }
    
    private static boolean isEndLocationSafe(Location location) {
        World world = location.getWorld();
        double y = location.getY();
        
        if (y < 10) {
            return false;
        }
        
        if (y > 120) {
            return false;
        }
        
        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();
        
        Block groundBlock = world.getBlockAt(blockX, blockY - 1, blockZ);
        Block feetBlock = world.getBlockAt(blockX, blockY, blockZ);
        Block headBlock = world.getBlockAt(blockX, blockY + 1, blockZ);
        
        if (!groundBlock.getType().isSolid()) {
            return false;
        }
        
        Material groundType = groundBlock.getType();
        if (groundType != Material.END_STONE && 
            groundType != Material.OBSIDIAN && 
            !groundType.isSolid()) {
            return false;
        }
        
        if (feetBlock.getType().isSolid() || headBlock.getType().isSolid()) {
            return false;
        }
        
        boolean hasGroundBelow = false;
        for (int checkY = blockY - 1; checkY > Math.max(0, blockY - 10); checkY--) {
            Block checkBlock = world.getBlockAt(blockX, checkY, blockZ);
            if (checkBlock.getType().isSolid()) {
                hasGroundBelow = true;
                break;
            }
        }
        
        if (!hasGroundBelow) {
            return false;
        }
        int voidCount = 0;
        for (int xOff = -1; xOff <= 1; xOff++) {
            for (int zOff = -1; zOff <= 1; zOff++) {
                if (xOff == 0 && zOff == 0) continue;
                Block nearbyGround = world.getBlockAt(blockX + xOff, blockY - 1, blockZ + zOff);
                if (!nearbyGround.getType().isSolid()) {
                    voidCount++;
                }
            }
        }
        
        if (voidCount > 3) {
            return false;
        }
        
        return true;
    }
    
    private static boolean isOverworldLocationSafe(Location location) {
        World world = location.getWorld();
        double y = location.getY();
        
        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();
        
        if (y < minHeight + 5) {
            return false; 
        }
        
        if (y > maxHeight - 10) {
            return false; 
        }
        
        if (y >= 127) {
            return false;
        }
        
        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();
        
        Block groundBlock = world.getBlockAt(blockX, blockY - 1, blockZ);
        Block feetBlock = world.getBlockAt(blockX, blockY, blockZ);
        Block headBlock = world.getBlockAt(blockX, blockY + 1, blockZ);
        
        if (!groundBlock.getType().isSolid()) {
            return false;
        }
        
        if (isDangerousBlock(groundBlock.getType())) {
            return false;
        }
        
        if (feetBlock.getType().isSolid() || headBlock.getType().isSolid()) {
            return false;
        }
        
        if (feetBlock.isLiquid() || headBlock.isLiquid() || groundBlock.isLiquid()) {
            return false;
        }
        
        if (hasLavaNearby(location)) {
            return false;
        }
        
        return true;
    }
    
    private static boolean isDangerousBlock(Material material) {
        switch (material) {
            case LAVA:
            case MAGMA_BLOCK:
            case FIRE:
            case SOUL_FIRE:
            case CAMPFIRE:
            case SOUL_CAMPFIRE:
            case CACTUS:
            case SWEET_BERRY_BUSH:
            case POWDER_SNOW:
            case WITHER_ROSE:
                return true;
            default:
                return false;
        }
    }
    
    private static boolean hasLavaNearby(Location location) {
        World world = location.getWorld();
        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();
        
        for (int xOff = -1; xOff <= 1; xOff++) {
            for (int yOff = -1; yOff <= 1; yOff++) {
                for (int zOff = -1; zOff <= 1; zOff++) {
                    Block block = world.getBlockAt(blockX + xOff, blockY + yOff, blockZ + zOff);
                    if (block.getType() == Material.LAVA) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    public static String getUnsafeReason(Location location) {
        if (location == null || location.getWorld() == null) {
            return "Location or world is null";
        }
        
        World world = location.getWorld();
        World.Environment env = world.getEnvironment();
        double y = location.getY();
        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();
        
        Block groundBlock = world.getBlockAt(blockX, blockY - 1, blockZ);
        Block feetBlock = world.getBlockAt(blockX, blockY, blockZ);
        Block headBlock = world.getBlockAt(blockX, blockY + 1, blockZ);
        
        if (env == World.Environment.NETHER) {
            if (y >= 126.0) {
                return "Nether: Y=" + y + " >= 126 (head would be at ceiling Y=127)";
            }
            if (y + 1.0 >= 127.0) {
                return "Nether: Head position Y=" + (y+1) + " >= 127 (ceiling)";
            }
            if (y < 5) {
                return "Nether: Y=" + y + " < 5 (too close to bottom bedrock)";
            }
            if (!groundBlock.getType().isSolid()) {
                return "Nether: No solid ground below (ground=" + groundBlock.getType() + ")";
            }
            if (isDangerousBlock(groundBlock.getType())) {
                return "Nether: Dangerous ground block (" + groundBlock.getType() + ")";
            }
            if (feetBlock.getType().isSolid() || headBlock.getType().isSolid()) {
                return "Nether: Player space obstructed by solid blocks (feet=" + feetBlock.getType() + ", head=" + headBlock.getType() + ")";
            }
            if (hasLavaNearby(location)) {
                return "Nether: Lava nearby";
            }
            Block ceilingBlock = world.getBlockAt(blockX, 127, blockZ);
            if (ceilingBlock.getType() == Material.BEDROCK && y >= 120) {
                return "Nether: Too close to bedrock ceiling (Y=" + y + " >= 120)";
            }
        } else if (env == World.Environment.THE_END) {
            if (y < 10) {
                return "End: Y=" + y + " < 10 (too close to void)";
            }
            if (y > 120) {
                return "End: Y=" + y + " > 120 (too high)";
            }
            if (!groundBlock.getType().isSolid()) {
                return "End: No solid ground (ground=" + groundBlock.getType() + ")";
            }
            Material groundType = groundBlock.getType();
            if (groundType != Material.END_STONE && groundType != Material.OBSIDIAN && !groundType.isSolid()) {
                return "End: Invalid ground material (" + groundType + ")";
            }
            if (feetBlock.getType().isSolid() || headBlock.getType().isSolid()) {
                return "End: Player space obstructed by solid blocks (feet=" + feetBlock.getType() + ", head=" + headBlock.getType() + ")";
            }
            boolean hasGroundBelow = false;
            for (int checkY = blockY - 1; checkY > Math.max(0, blockY - 10); checkY--) {
                Block checkBlock = world.getBlockAt(blockX, checkY, blockZ);
                if (checkBlock.getType().isSolid()) {
                    hasGroundBelow = true;
                    break;
                }
            }
            if (!hasGroundBelow) {
                return "End: No solid ground within 10 blocks below (floating island too small)";
            }
            int voidCount = 0;
            for (int xOff = -1; xOff <= 1; xOff++) {
                for (int zOff = -1; zOff <= 1; zOff++) {
                    if (xOff == 0 && zOff == 0) continue;
                    Block nearbyGround = world.getBlockAt(blockX + xOff, blockY - 1, blockZ + zOff);
                    if (!nearbyGround.getType().isSolid()) {
                        voidCount++;
                    }
                }
            }
            if (voidCount > 3) {
                return "End: Too many void blocks nearby (surrounded by void: " + voidCount + "/8)";
            }
        } else {
            int minHeight = world.getMinHeight();
            int maxHeight = world.getMaxHeight();
            
            if (y < minHeight + 5) {
                return "Overworld: Y=" + y + " too close to bottom (minHeight=" + minHeight + ")";
            }
            if (y > maxHeight - 10) {
                return "Overworld: Y=" + y + " too close to top (maxHeight=" + maxHeight + ")";
            }
            if (y >= 127) {
                return "Overworld: Y=" + y + " >= 127 (invalid height)";
            }
            if (!groundBlock.getType().isSolid()) {
                return "Overworld: No solid ground (ground=" + groundBlock.getType() + ")";
            }
            if (isDangerousBlock(groundBlock.getType())) {
                return "Overworld: Dangerous ground block (" + groundBlock.getType() + ")";
            }
            if (feetBlock.getType().isSolid() || headBlock.getType().isSolid()) {
                return "Overworld: Player space obstructed by solid blocks (feet=" + feetBlock.getType() + ", head=" + headBlock.getType() + ")";
            }
            if (feetBlock.isLiquid() || headBlock.isLiquid() || groundBlock.isLiquid()) {
                return "Overworld: Liquid detected (feet=" + feetBlock.isLiquid() + ", head=" + headBlock.isLiquid() + ", ground=" + groundBlock.isLiquid() + ")";
            }
            if (hasLavaNearby(location)) {
                return "Overworld: Lava nearby";
            }
        }
        
        return "Unknown safety issue (all checks passed but safety returned false)";
    }

    public static CompletableFuture<Boolean> isLocationAbsolutelySafeAsync(Location location) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (location == null || location.getWorld() == null) {
            future.complete(false);
            return future;
        }

        if (Bukkit.isPrimaryThread()) {
            try {
                future.complete(isLocationAbsolutelySafe(location));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        } else {
            Bukkit.getScheduler().runTask(JustRTP.getInstance(), () -> {
                try {
                    future.complete(isLocationAbsolutelySafe(location));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        }

        return future;
    }
}
