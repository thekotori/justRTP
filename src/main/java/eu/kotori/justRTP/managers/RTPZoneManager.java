package eu.kotori.justRTP.managers;

import eu.kotori.justRTP.JustRTP;
import eu.kotori.justRTP.utils.RTPZone;
import eu.kotori.justRTP.utils.SafetyValidator;
import eu.kotori.justRTP.utils.task.CancellableTask;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class RTPZoneManager {

    private final JustRTP plugin;
    private final Map<String, RTPZone> zones = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerZoneMap = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> zonePlayersMap = new ConcurrentHashMap<>();
    private final Map<String, CancellableTask> activeZoneTasks = new ConcurrentHashMap<>();
    private final Set<UUID> ignoringPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> recentlyTeleported = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> zoneCountdowns = new ConcurrentHashMap<>();
    private File zonesFile;
    private FileConfiguration zonesConfig;
    private CancellableTask hologramHealerTask;

    public RTPZoneManager(JustRTP plugin) {
        this.plugin = plugin;
        this.zonesFile = new File(plugin.getDataFolder(), "rtp_zones.yml");
    }

    public void loadZones() {
        if (!zonesFile.exists()) {
            plugin.saveResource("rtp_zones.yml", false);
        }
        zonesConfig = YamlConfiguration.loadConfiguration(zonesFile);
        shutdownAllTasks();
        zones.clear();
        playerZoneMap.clear();
        zonePlayersMap.clear();
        ignoringPlayers.clear();
        recentlyTeleported.clear();

        ConfigurationSection zonesSection = zonesConfig.getConfigurationSection("zones");
        if (zonesSection == null) {
            plugin.getLogger().info("No RTP zones found to load.");
            return;
        }

        for (String zoneId : zonesSection.getKeys(false)) {
            try {
                RTPZone zone = new RTPZone(zoneId, zonesSection.getConfigurationSection(zoneId));
                zones.put(zoneId.toLowerCase(), zone);
                startZoneScheduler(zone);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Failed to load RTP Zone '" + zoneId + "': " + e.getMessage());
            }
        }
        startHologramHealerTask();
        plugin.getLogger().info("Loaded and activated " + zones.size() + " RTP Arena Zones.");
    }

    private void startZoneScheduler(RTPZone zone) {
        CancellableTask existingTask = activeZoneTasks.remove(zone.getId().toLowerCase());
        if (existingTask != null) {
            existingTask.cancel();
        }

        Location zoneCenter = zone.getCenterLocation();
        if (zoneCenter == null) {
            plugin.getLogger().severe("Could not start scheduler for zone '" + zone.getId() + "' because its center location is in an unloaded world.");
            return;
        }

        if (zone.getHologramLocation() != null) {
            plugin.getHologramManager().createOrUpdateHologram(zone.getId(), zone.getHologramLocation(), zone.getHologramViewDistance());
        }

        final int interval = zone.getInterval();
        final java.util.concurrent.atomic.AtomicInteger countdown = new java.util.concurrent.atomic.AtomicInteger(interval);

        zoneCountdowns.put(zone.getId().toLowerCase(), interval);
        
        plugin.getHologramManager().updateHologramTime(zone.getId(), interval);

        CancellableTask task = plugin.getFoliaScheduler().runTimerAtLocation(zoneCenter, () -> {
            try {
                List<Player> playersInZone = getPlayersInZone(zone.getId());

                if (countdown.get() <= 0) {
                    plugin.getHologramManager().updateHologramProgress(zone.getId());
                    
                    if (!playersInZone.isEmpty()) {
                        for (Player player : playersInZone) {
                            if (!isIgnoring(player)) {
                                plugin.getFoliaScheduler().runAtEntity(player, () -> {
                                    player.clearTitle();
                                });
                            }
                        }
                        
                        teleportPlayersInZone(playersInZone, zone);
                    }
                    
                    countdown.set(interval);
                    zoneCountdowns.put(zone.getId().toLowerCase(), interval);
                    plugin.getHologramManager().updateHologramTime(zone.getId(), interval);
                    return;
                }

                int currentTime = countdown.decrementAndGet();
                
                zoneCountdowns.put(zone.getId().toLowerCase(), currentTime);
                
                plugin.getHologramManager().updateHologramTime(zone.getId(), currentTime);

                for (Player player : playersInZone) {
                    if (!isIgnoring(player)) {
                        final int timeToPass = currentTime;
                        plugin.getFoliaScheduler().runAtEntity(player, () -> updateWaitingEffects(player, zone, timeToPass));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error in RTPZone scheduler for zone '" + zone.getId() + "':", e);
            }
        }, 1L, 20L);

        activeZoneTasks.put(zone.getId().toLowerCase(), task);
    }

    private void startHologramHealerTask() {
        if (hologramHealerTask != null && !hologramHealerTask.isCancelled()) {
            hologramHealerTask.cancel();
        }
        hologramHealerTask = plugin.getFoliaScheduler().runTimer(() -> {
            for (RTPZone zone : zones.values()) {
                Location holoLoc = zone.getHologramLocation();
                if (holoLoc != null && holoLoc.getWorld().isChunkLoaded(holoLoc.getBlockX() >> 4, holoLoc.getBlockZ() >> 4)) {
                    if (!plugin.getHologramManager().isHologramActive(zone.getId()) && !plugin.getHologramManager().isBeingCreated(zone.getId())) {
                        plugin.debug("Healer task is respawning missing hologram for zone: " + zone.getId());
                        plugin.getHologramManager().createOrUpdateHologram(zone.getId(), holoLoc, zone.getHologramViewDistance());
                    }
                }
            }
        }, 100L, 100L);
    }

    private void teleportPlayersInZone(List<Player> players, RTPZone zone) {
        List<Player> teleportCandidates = players.stream()
                .filter(p -> p != null && p.isOnline() && !isIgnoring(p))
                .collect(Collectors.toList());

        if (teleportCandidates.isEmpty()) {
            plugin.debug("No valid teleport candidates for zone " + zone.getId());
            return;
        }

        plugin.debug("Teleporting " + teleportCandidates.size() + " players from zone " + zone.getId());

        Set<UUID> playersInThisZone = zonePlayersMap.get(zone.getId().toLowerCase());
        if (playersInThisZone != null) {
            teleportCandidates.forEach(p -> {
                UUID playerUUID = p.getUniqueId();
                
                playerZoneMap.remove(playerUUID);
                playersInThisZone.remove(playerUUID);
                
                recentlyTeleported.add(playerUUID);
                plugin.getFoliaScheduler().runLater(() -> recentlyTeleported.remove(playerUUID), 100L);

                plugin.getFoliaScheduler().runAtEntity(p, () -> {
                    p.clearTitle();
                    plugin.getEffectsManager().clearActionBar(p);
                    plugin.getEffectsManager().applyEffects(p, getZoneEffects(zone, "teleport"));
                });
            });
        }

        World targetWorld = Bukkit.getWorld(zone.getTarget());
        if (targetWorld != null) {
            handleLocalZoneTeleport(teleportCandidates, zone, targetWorld);
        } else if (plugin.getConfigManager().getProxyEnabled()) {
            handleProxyZoneTeleport(teleportCandidates, zone);
        } else {
            plugin.getLogger().warning("Zone '" + zone.getId() + "' target world '" + zone.getTarget() + 
                                     "' not found and proxy is disabled. Teleport aborted for " + 
                                     teleportCandidates.size() + " players.");
            teleportCandidates.forEach(p -> 
                plugin.getFoliaScheduler().runAtEntity(p, () -> 
                    plugin.getLocaleManager().sendMessage(p, "command.world_not_found")
                )
            );
        }
    }

    private void handleProxyZoneTeleport(List<Player> players, RTPZone zone) {
        plugin.getCrossServerManager().sendGroupFindLocationRequest(players, zone.getTarget(), Optional.of(zone.getMinRadius()), Optional.of(zone.getMaxRadius()));
    }

    private void handleLocalZoneTeleport(List<Player> players, RTPZone zone, World targetWorld) {
        plugin.getLogger().info("[ZONE RTP] Starting zone teleport for " + players.size() + " player(s) in zone '" + 
                               zone.getId() + "' to world '" + targetWorld.getName() + "' (" + targetWorld.getEnvironment() + ")");
        
        if (targetWorld.getEnvironment() == World.Environment.NETHER) {
            plugin.getLogger().info("╔════════════════════════════════════════════╗");
            plugin.getLogger().info("║  NETHER ZONE TELEPORT INITIATED           ║");
            plugin.getLogger().info("║  World: " + targetWorld.getName() + "              ║");
            plugin.getLogger().info("║  Environment: NETHER                      ║");
            plugin.getLogger().info("║  Safety: Y < 127 ENFORCED                 ║");
            plugin.getLogger().info("╚════════════════════════════════════════════╝");
        }
        
        findSafeLocationsForPlayers(players, zone, targetWorld);
    }

    private void findSafeLocationsForPlayers(List<Player> players, RTPZone zone, World targetWorld) {
        plugin.debug("[ZONE RTP] Finding nearby safe locations for " + players.size() + " player(s)");
        
        plugin.getHologramManager().updateHologramProgress(zone.getId());
        
        int minSpread = zone.getMinSpreadDistance();
        int maxSpread = zone.getMaxSpreadDistance();
        
        plugin.debug("[ZONE RTP] Players will spawn within " + minSpread + " to " + maxSpread + " blocks of central location");
        
        Player firstPlayer = players.get(0);
        plugin.debug("[ZONE RTP] Finding central safe location using " + firstPlayer.getName() + " as reference");
        
        plugin.getRtpService()
            .findSafeLocation(firstPlayer, targetWorld, 0, 
                            Optional.of(zone.getMinRadius()), 
                            Optional.of(zone.getMaxRadius()))
            .thenAccept(centralLocationOpt -> {
                if (!centralLocationOpt.isPresent()) {
                    plugin.getLogger().warning("[ZONE RTP] Could not find central safe location for zone " + zone.getId());
                    
                    for (Player player : players) {
                        if (player != null && player.isOnline()) {
                            plugin.getFoliaScheduler().runAtEntity(player, () -> {
                                plugin.getLocaleManager().sendMessage(player, "teleport.no_location_found");
                                plugin.getLocaleManager().sendMessage(player, "zone.teleport_failed");
                            });
                        }
                    }
                    return;
                }
                
                Location centralLocation = centralLocationOpt.get();
                plugin.getLogger().info("[ZONE RTP] Central location found at " + 
                                      centralLocation.getBlockX() + "," + centralLocation.getBlockY() + "," + 
                                      centralLocation.getBlockZ() + " in " + targetWorld.getName());
                
                List<CompletableFuture<Optional<Location>>> locationFutures = new ArrayList<>();
                List<Location> foundLocations = Collections.synchronizedList(new ArrayList<>());
                
                for (int i = 0; i < players.size(); i++) {
                    final Player player = players.get(i);
                    final int playerIndex = i;
                    
                    CompletableFuture<Optional<Location>> future = findSafeLocationNearby(
                        player, centralLocation, minSpread, maxSpread, foundLocations, playerIndex, targetWorld
                    );
                    
                    locationFutures.add(future);
                }
                
                completeTeleportation(locationFutures, players, zone, targetWorld, centralLocation);
            })
            .exceptionally(throwable -> {
                plugin.getLogger().severe("[ZONE RTP] Error finding central location: " + throwable.getMessage());
                throwable.printStackTrace();
                return null;
            });
    }
    
    private CompletableFuture<Optional<Location>> findSafeLocationNearby(
            Player player, Location centralLocation, int minSpread, int maxSpread,
            List<Location> foundLocations, int playerIndex, World targetWorld) {
        
        double angle = Math.random() * 2 * Math.PI;
        double distance = minSpread + (Math.random() * (maxSpread - minSpread)); 
        int offsetX = (int) (Math.cos(angle) * distance);
        int offsetZ = (int) (Math.sin(angle) * distance);
        
        Location targetLocation = centralLocation.clone().add(offsetX, 0, offsetZ);
        
        plugin.debug("[ZONE RTP] Player " + playerIndex + " searching near offset " + 
                   offsetX + "," + offsetZ + " (distance: " + String.format("%.1f", distance) + ")");
        
        return findClosestSafeLocation(player, targetLocation, maxSpread, targetWorld)
            .thenApply(locationOpt -> {
                if (locationOpt.isPresent()) {
                    Location safeLocation = locationOpt.get();
                    synchronized (foundLocations) {
                        boolean tooClose = foundLocations.stream()
                            .anyMatch(existing -> existing.distance(safeLocation) < minSpread);
                        
                        if (tooClose && foundLocations.size() > 0) {
                            plugin.debug("[ZONE RTP] Player " + playerIndex + " location too close, trying different angle");
                            
                            for (int attempt = 0; attempt < 3; attempt++) {
                                double newAngle = Math.random() * 2 * Math.PI;
                                double newDistance = minSpread + (Math.random() * (maxSpread - minSpread));
                                int newOffsetX = (int) (Math.cos(newAngle) * newDistance);
                                int newOffsetZ = (int) (Math.sin(newAngle) * newDistance);
                                
                                Location newTarget = centralLocation.clone().add(newOffsetX, 0, newOffsetZ);
                                
                                try {
                                    Optional<Location> newLocation = findClosestSafeLocation(
                                        player, newTarget, maxSpread, targetWorld
                                    ).join();
                                    
                                    if (newLocation.isPresent()) {
                                        Location newCandidate = newLocation.get();
                                        boolean stillTooClose = foundLocations.stream()
                                            .anyMatch(existing -> existing.distance(newCandidate) < minSpread);
                                        
                                        if (!stillTooClose) {
                                            foundLocations.add(newCandidate);
                                            plugin.debug("[ZONE RTP] Found better spread location for player " + playerIndex);
                                            return Optional.of(newCandidate);
                                        }
                                    }
                                } catch (Exception e) {
                                    plugin.debug("[ZONE RTP] Retry attempt failed: " + e.getMessage());
                                }
                            }
                        }
                        
                        foundLocations.add(safeLocation);
                    }
                }
                return locationOpt;
            });
    }
    
    private CompletableFuture<Optional<Location>> findClosestSafeLocation(
            Player player, Location targetLocation, int searchRadius, World targetWorld) {
        
        int targetX = targetLocation.getBlockX();
        int targetZ = targetLocation.getBlockZ();
        
        Location worldSpawn = targetWorld.getSpawnLocation();
        int distanceFromSpawn = (int) Math.sqrt(
            Math.pow(targetX - worldSpawn.getBlockX(), 2) + 
            Math.pow(targetZ - worldSpawn.getBlockZ(), 2)
        );
        
        int searchMin = Math.max(0, distanceFromSpawn - searchRadius);
        int searchMax = distanceFromSpawn + searchRadius;
        
        return plugin.getRtpService().findSafeLocation(
            player, targetWorld, 0,
            Optional.of(searchMin),
            Optional.of(searchMax)
        );
    }
    
    private void completeTeleportation(
            List<CompletableFuture<Optional<Location>>> locationFutures,
            List<Player> players, RTPZone zone, World targetWorld, Location centralLocation) {

        CompletableFuture.allOf(locationFutures.toArray(new CompletableFuture[0]))
            .thenAccept(v -> {
                int successCount = 0;
                int failCount = 0;
                
                plugin.debug("[ZONE RTP] All location searches completed, starting teleportation phase");
                
                for (int i = 0; i < players.size(); i++) {
                    Player player = players.get(i);
                    
                    if (player == null || !player.isOnline()) {
                        plugin.debug("[ZONE RTP] Player " + (player != null ? player.getName() : "unknown") + 
                                   " is no longer online, skipping teleport");
                        failCount++;
                        continue;
                    }
                    
                    Optional<Location> locationOpt = locationFutures.get(i).join();
                    
                    if (locationOpt.isPresent()) {
                        Location safeSpot = locationOpt.get();
                        
                        if (!SafetyValidator.isLocationAbsolutelySafe(safeSpot)) {
                            String reason = SafetyValidator.getUnsafeReason(safeSpot);
                            plugin.getLogger().severe("╔════════════════════════════════════════════════════════════╗");
                            plugin.getLogger().severe("║  ZONE TELEPORT SAFETY VALIDATOR BLOCKED UNSAFE LOCATION!  ║");
                            plugin.getLogger().severe("║  Player: " + player.getName() + "                         ║");
                            plugin.getLogger().severe("║  World: " + targetWorld.getName() + " (" + targetWorld.getEnvironment() + ")  ║");
                            plugin.getLogger().severe("║  Location: " + safeSpot.getBlockX() + "," + safeSpot.getBlockY() + "," + safeSpot.getBlockZ() + "  ║");
                            plugin.getLogger().severe("║  Reason: " + reason + "                                   ║");
                            plugin.getLogger().severe("║  THIS IS A CRITICAL SAFETY FAILURE - PLEASE REPORT!       ║");
                            plugin.getLogger().severe("╚════════════════════════════════════════════════════════════╝");
                            failCount++;
                            plugin.getFoliaScheduler().runAtEntity(player, () -> {
                                plugin.getLocaleManager().sendMessage(player, "teleport.no_location_found");
                                plugin.getLocaleManager().sendMessage(player, "zone.teleport_failed");
                            });
                            continue;
                        }
                        
                        if (targetWorld.getEnvironment() == World.Environment.NETHER) {
                            double y = safeSpot.getY();
                            if (y >= 126.0) {
                                plugin.getLogger().severe("╔════════════════════════════════════════════════════════════╗");
                                plugin.getLogger().severe("║  EMERGENCY: NETHER ROOF SPAWN BLOCKED IN ZONE!            ║");
                                plugin.getLogger().severe("║  Player: " + player.getName() + "                         ║");
                                plugin.getLogger().severe("║  Location: Y=" + y + " >= 126 (head at Y=" + (y+1) + ")   ║");
                                plugin.getLogger().severe("║  This should NEVER happen - RTPService failed!             ║");
                                plugin.getLogger().severe("╚════════════════════════════════════════════════════════════╝");
                                failCount++;
                                plugin.getFoliaScheduler().runAtEntity(player, () -> {
                                    plugin.getLocaleManager().sendMessage(player, "teleport.no_location_found");
                                    plugin.getLocaleManager().sendMessage(player, "zone.teleport_failed");
                                });
                                continue;
                            }
                            plugin.getLogger().info("[ZONE RTP - NETHER SAFE] ✓ Verified Y=" + y + " < 126 (head at Y=" + (y+1) + ") for " + player.getName());
                        } else if (targetWorld.getEnvironment() == World.Environment.THE_END) {
                            double y = safeSpot.getY();
                            if (y < 10 || y > 120) {
                                plugin.getLogger().severe("[ZONE RTP - END SAFETY] Rejected Y=" + y + " (out of range 10-120) for " + player.getName());
                                failCount++;
                                plugin.getFoliaScheduler().runAtEntity(player, () -> {
                                    plugin.getLocaleManager().sendMessage(player, "teleport.no_location_found");
                                    plugin.getLocaleManager().sendMessage(player, "zone.teleport_failed");
                                });
                                continue;
                            }
                            plugin.getLogger().info("[ZONE RTP - END SAFE] ✓ Verified Y=" + y + " (range 10-120) for " + player.getName());
                        }
                        
                        successCount++;
                        
                        plugin.getLogger().info("[ZONE RTP] Found safe location for " + player.getName() + 
                                              " at Y=" + safeSpot.getY() + " in " + targetWorld.getName());
                        
                        plugin.getFoliaScheduler().runAtEntity(player, () -> {
                            plugin.getRtpService().teleportPlayer(player, safeSpot);
                            plugin.getLocaleManager().sendMessage(player, "zone.teleport_success");
                            plugin.debug("[ZONE RTP] Successfully teleported " + player.getName() + 
                                       " to " + safeSpot.getBlockX() + "," + safeSpot.getBlockY() + "," + 
                                       safeSpot.getBlockZ() + " in " + targetWorld.getName());
                        });
                    } else {
                        failCount++;
                        plugin.getLogger().warning("[ZONE RTP] Could not find safe location for " + 
                                                 player.getName() + " in zone " + zone.getId() + 
                                                 " after all attempts");
                        plugin.getFoliaScheduler().runAtEntity(player, () -> {
                            plugin.getLocaleManager().sendMessage(player, "teleport.no_location_found");
                            plugin.getLocaleManager().sendMessage(player, "zone.teleport_failed");
                        });
                    }
                }
                
                final int finalSuccess = successCount;
                final int finalFail = failCount;
                
                plugin.getLogger().info("[ZONE RTP] Zone '" + zone.getId() + "' group teleport complete:");
                plugin.getLogger().info("  Central Location: " + centralLocation.getBlockX() + "," + 
                                      centralLocation.getBlockY() + "," + centralLocation.getBlockZ());
                plugin.getLogger().info("  Players: " + finalSuccess + "/" + players.size() + " teleported successfully" +
                                      (finalFail > 0 ? ", " + finalFail + " failed" : ""));
                plugin.getLogger().info("  All players spawned within " + zone.getMaxSpreadDistance() + 
                                      " blocks of central location");
                
                if (finalFail > 0) {
                    plugin.getLogger().warning("[ZONE RTP] Zone " + zone.getId() + " had " + finalFail + 
                                             " failed teleports. Check world configuration and zone radius settings.");
                }
                
                plugin.getFoliaScheduler().runLater(() -> {
                    plugin.getHologramManager().updateHologramTime(zone.getId(), zone.getInterval());
                    plugin.debug("[ZONE RTP] Zone '" + zone.getId() + "' reset hologram to " + zone.getInterval() + "s");
                }, 20L); 
            })
            .exceptionally(throwable -> {
                plugin.getLogger().severe("[ZONE RTP] Critical error during zone teleport for zone " + 
                                        zone.getId() + ": " + throwable.getMessage());
                throwable.printStackTrace();
                
                for (Player player : players) {
                    if (player != null && player.isOnline()) {
                        plugin.getFoliaScheduler().runAtEntity(player, () -> {
                            plugin.getLocaleManager().sendMessage(player, "zone.teleport_failed");
                        });
                    }
                }
                return null;
            });
    }

    private void updateWaitingEffects(Player player, RTPZone zone, int timeRemaining) {
        ConfigurationSection waitingEffects = getZoneEffects(zone, "waiting");
        if (waitingEffects == null) return;

        if ((timeRemaining <= 5 && timeRemaining > 0) || timeRemaining % 5 == 0) {
            ConfigurationSection titleSection = waitingEffects.getConfigurationSection("title");
            if (titleSection != null && titleSection.getBoolean("enabled", false)) {
                String titleText = titleSection.getString("main_title", "");
                String subtitleText = titleSection.getString("subtitle", "");
                if(!titleText.isBlank() || !subtitleText.isBlank()){
                    long fadeIn = titleSection.getLong("fade_in", 0);
                    long stay = titleSection.getLong("stay", 25);
                    long fadeOut = titleSection.getLong("fade_out", 5);

                    Title.Times times = Title.Times.times(Duration.ofMillis(fadeIn * 50), Duration.ofMillis(stay * 50), Duration.ofMillis(fadeOut * 50));
                    Title title = Title.title(
                            MiniMessage.miniMessage().deserialize(titleText),
                            MiniMessage.miniMessage().deserialize(subtitleText, Placeholder.unparsed("time", String.valueOf(timeRemaining))),
                            times
                    );
                    player.showTitle(title);
                }
            }
        }

        ConfigurationSection actionBarSection = waitingEffects.getConfigurationSection("action_bar");
        if (actionBarSection != null && actionBarSection.getBoolean("enabled", false)) {
            String text = actionBarSection.getString("text", "");
            if(!text.isBlank()) {
                player.sendActionBar(MiniMessage.miniMessage().deserialize(text, Placeholder.unparsed("time", String.valueOf(timeRemaining))));
            }
        }

        ConfigurationSection soundSection = waitingEffects.getConfigurationSection("sound");
        if (soundSection != null && soundSection.getBoolean("enabled", false) && timeRemaining <= 3 && timeRemaining > 0) {
            try {
                Sound sound = Sound.valueOf(soundSection.getString("name", "").toUpperCase());
                float volume = (float) soundSection.getDouble("volume", 1.0);
                float pitch = (float) soundSection.getDouble("pitch", 1.0);
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException e) {
            }
        }
    }

    private RTPZone getZoneAt(Location location) {
        for (RTPZone zone : zones.values()) {
            if (zone.contains(location)) {
                return zone;
            }
        }
        return null;
    }

    public void handlePlayerMove(Player player, Location to) {
        if (recentlyTeleported.contains(player.getUniqueId())) {
            return;
        }

        String currentZoneId = playerZoneMap.get(player.getUniqueId());
        RTPZone newZone = getZoneAt(to);

        if (newZone != null) {
            if (!Objects.equals(currentZoneId, newZone.getId())) {
                if (currentZoneId != null) {
                    RTPZone oldZone = getZone(currentZoneId);
                    if (oldZone != null) {
                        plugin.getEffectsManager().applyEffects(player, getZoneEffects(oldZone, "on_leave"));
                    }
                    Set<UUID> playersInOldZone = zonePlayersMap.get(currentZoneId.toLowerCase());
                    if (playersInOldZone != null) {
                        playersInOldZone.remove(player.getUniqueId());
                    }
                }
                playerZoneMap.put(player.getUniqueId(), newZone.getId());
                zonePlayersMap.computeIfAbsent(newZone.getId().toLowerCase(), k -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());
                plugin.getEffectsManager().applyEffects(player, getZoneEffects(newZone, "on_enter"));
            }
        } else {
            if (currentZoneId != null) {
                RTPZone oldZone = getZone(currentZoneId);
                if (oldZone != null) {
                    plugin.getEffectsManager().applyEffects(player, getZoneEffects(oldZone, "on_leave"));
                }
                playerZoneMap.remove(player.getUniqueId());
                Set<UUID> playersInOldZone = zonePlayersMap.get(currentZoneId.toLowerCase());
                if (playersInOldZone != null) {
                    playersInOldZone.remove(player.getUniqueId());
                }
            }
        }
    }

    public void handlePlayerQuit(Player player) {
        String zoneId = playerZoneMap.remove(player.getUniqueId());
        if (zoneId != null) {
            Set<UUID> players = zonePlayersMap.get(zoneId.toLowerCase());
            if (players != null) {
                players.remove(player.getUniqueId());
            }
        }
        ignoringPlayers.remove(player.getUniqueId());
        recentlyTeleported.remove(player.getUniqueId());
    }

    private List<Player> getPlayersInZone(String zoneId) {
        Set<UUID> playerUuids = zonePlayersMap.get(zoneId.toLowerCase());
        if (playerUuids == null) {
            return Collections.emptyList();
        }
        return playerUuids.stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline())
                .collect(Collectors.toList());
    }

    private ConfigurationSection getZoneEffects(RTPZone zone, String effectType) {
        String path = "zones." + zone.getId() + ".effects." + effectType;
        if (zonesConfig.isConfigurationSection(path)) {
            return zonesConfig.getConfigurationSection(path);
        }
        return plugin.getConfig().getConfigurationSection("zone_effects." + effectType);
    }

    public RTPZone getZone(String zoneId) {
        return zones.get(zoneId.toLowerCase());
    }

    public void shutdownAllTasks() {
        activeZoneTasks.values().forEach(CancellableTask::cancel);
        activeZoneTasks.clear();
        if (hologramHealerTask != null && !hologramHealerTask.isCancelled()) {
            hologramHealerTask.cancel();
        }
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().cleanupAllHolograms();
        }
    }

    public void toggleIgnore(Player player) {
        if(ignoringPlayers.contains(player.getUniqueId())) {
            ignoringPlayers.remove(player.getUniqueId());
            plugin.getLocaleManager().sendMessage(player, "zone.command.ignore_disabled");
        } else {
            ignoringPlayers.add(player.getUniqueId());
            plugin.getLocaleManager().sendMessage(player, "zone.command.ignore_enabled");
        }
    }

    public boolean isIgnoring(Player player) {
        return ignoringPlayers.contains(player.getUniqueId());
    }

    public void saveZone(RTPZone zone) {
        zones.put(zone.getId().toLowerCase(), zone);
        ConfigurationSection section = zonesConfig.getConfigurationSection("zones." + zone.getId());
        if (section == null) {
            section = zonesConfig.createSection("zones." + zone.getId());
        }
        zone.serialize(section);
        try {
            zonesConfig.save(zonesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save RTP Zone to file: " + e.getMessage());
        }
    }

    public void setHologramForZone(Player player, String zoneId, Location location, int viewDistance) {
        RTPZone zone = getZone(zoneId);
        if (zone == null) {
            return;
        }
        zone.setHologramData(location, viewDistance);
        saveZone(zone);
        startZoneScheduler(zone);
    }

    public void deleteHologramForZone(Player player, String zoneId) {
        RTPZone zone = getZone(zoneId);
        if (zone == null) {
            plugin.getLocaleManager().sendMessage(player, "zone.error.not_found", Placeholder.unparsed("id", zoneId));
            return;
        }
        if(zone.getHologramLocation() == null) {
            plugin.getLocaleManager().sendMessage(player, "zone.command.hologram_not_found", Placeholder.unparsed("id", zoneId));
            return;
        }
        zone.setHologramData(null, 0);
        saveZone(zone);
        plugin.getHologramManager().removeHologram(zoneId);
        plugin.getLocaleManager().sendMessage(player, "zone.command.delhologram_success", Placeholder.unparsed("id", zoneId));
    }

    public void deleteZone(Player player, String zoneId) {
        String lowerId = zoneId.toLowerCase();
        if (!zones.containsKey(lowerId)) {
            plugin.getLocaleManager().sendMessage(player, "zone.error.not_found", Placeholder.unparsed("id", zoneId));
            return;
        }
        CancellableTask task = activeZoneTasks.remove(lowerId);
        if (task != null) {
            task.cancel();
        }

        plugin.getHologramManager().removeHologram(zoneId);

        zones.remove(lowerId);
        zonesConfig.set("zones." + zoneId, null);
        try {
            zonesConfig.save(zonesFile);
            plugin.getLocaleManager().sendMessage(player, "zone.command.delete_success", Placeholder.unparsed("id", zoneId));
        } catch (IOException e) {
            plugin.getLogger().severe("Could not delete RTP Zone from file: " + e.getMessage());
            plugin.getLocaleManager().sendMessage(player, "zone.error.save_failed");
        }
    }

    public void listZones(Player player) {
        if (zones.isEmpty()) {
            plugin.getLocaleManager().sendMessage(player, "zone.command.list_empty");
            return;
        }
        MiniMessage mm = MiniMessage.miniMessage();
        plugin.getLocaleManager().sendMessage(player, "zone.command.list_header");
        for (RTPZone zone : zones.values()) {
            player.sendMessage(mm.deserialize(
                    plugin.getLocaleManager().getRawMessage("zone.command.list_format"),
                    Placeholder.unparsed("id", zone.getId()),
                    Placeholder.unparsed("target", zone.getTarget())
            ));
        }
    }

    public int getZoneCountdown(String zoneId) {
        return zoneCountdowns.getOrDefault(zoneId.toLowerCase(), -1);
    }
    
    public String getPlayerZone(Player player) {
        return playerZoneMap.get(player.getUniqueId());
    }
    
    public Collection<RTPZone> getAllZones() {
        return zones.values();
    }

    public boolean zoneExists(String id) {
        return zones.containsKey(id.toLowerCase());
    }

    public Set<String> getZoneIds() {
        return zones.keySet();
    }
}