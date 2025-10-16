package eu.kotori.justRTP.managers;

import eu.kotori.justRTP.JustRTP;
import eu.kotori.justRTP.utils.DatabaseManager.ProxyTeleportRequest;
import eu.kotori.justRTP.utils.task.CancellableTask;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CrossServerManager {
    private final JustRTP plugin;
    private final Map<UUID, CancellableTask> activeTimers = new ConcurrentHashMap<>();
    private CancellableTask mysqlPollingTask;

    public CrossServerManager(JustRTP plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        startMySqlPolling();
    }

    public void startMySqlPolling() {
        if (mysqlPollingTask != null && !mysqlPollingTask.isCancelled()) {
            mysqlPollingTask.cancel();
        }
        if (!plugin.getConfigManager().isProxyMySqlEnabled() || plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            plugin.debug("MySQL polling is disabled as MySQL is not configured or connected.");
            return;
        }

        plugin.debug("Starting MySQL polling tasks for cross-server teleports.");
        String thisServerName = plugin.getConfigManager().getProxyThisServerName();
        if (thisServerName == null || thisServerName.isEmpty() || thisServerName.equals("server-name")) {
            plugin.getLogger().severe("'this_server_name' is not set correctly in config.yml! Cross-server features will not work.");
            return;
        }

        mysqlPollingTask = plugin.getFoliaScheduler().runTimer(() -> {
            plugin.getDatabaseManager().findAndMarkPendingTeleportRequestForServer(thisServerName).thenAccept(requestOpt ->
                    requestOpt.ifPresent(this::handleFindLocation)
            );

            plugin.getDatabaseManager().findFinalizedTeleportRequestsByOrigin(thisServerName).thenAccept(requests ->
                    requests.forEach(this::handleFinalizedRequest)
            );
        }, 60L, 40L);
    }

    public void sendFindLocationRequest(Player player, String targetServer, String targetWorldName, Optional<Integer> minRadius, Optional<Integer> maxRadius, String[] args) {
        plugin.debug("Preparing to send FindLocationRequest for " + player.getName() + " to server " + targetServer);
        String argsString = String.join(" ", args);
        
        plugin.getDatabaseManager().createTeleportRequest(player.getUniqueId(), plugin.getConfigManager().getProxyThisServerName(), targetServer, argsString, targetWorldName, minRadius, maxRadius, "INDIVIDUAL")
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        plugin.getLogger().severe("Failed to create cross-server teleport request for " + player.getName() + ": " + throwable.getMessage());
                        plugin.getLocaleManager().sendMessage(player, "proxy.request_failed");
                        plugin.getCooldownManager().clearCooldown(player.getUniqueId());
                    } else {
                        plugin.debug("Cross-server teleport request created successfully for " + player.getName());
                        startQueueTimer(player, targetServer);
                    }
                });
    }

    public void sendGroupFindLocationRequest(List<Player> players, String targetServer, Optional<Integer> minRadius, Optional<Integer> maxRadius) {
        if (players.isEmpty()) return;

        Player leader = players.get(0);
        List<Player> members = players.subList(1, players.size());
        String memberUuidsString = members.stream()
                .map(p -> p.getUniqueId().toString())
                .collect(Collectors.joining(","));

        String commandArgs = "GROUP_TELEPORT:" + memberUuidsString;

        String targetWorldName = null;
        if (targetServer.contains(":")) {
            String[] parts = targetServer.split(":", 2);
            targetServer = parts[0];
            targetWorldName = parts[1];
        }

        plugin.getDatabaseManager().createTeleportRequest(leader.getUniqueId(), plugin.getConfigManager().getProxyThisServerName(), targetServer, commandArgs, targetWorldName, minRadius, maxRadius, "GROUP_LEADER");

        for (Player member : members) {
            plugin.getDatabaseManager().createTeleportRequest(member.getUniqueId(), plugin.getConfigManager().getProxyThisServerName(), targetServer, "GROUP_TELEPORT_MEMBER", targetWorldName, minRadius, maxRadius, "GROUP_MEMBER");
        }


        String serverAlias = plugin.getConfigManager().getProxyServerAlias(targetServer);
        for(Player p : players) {
            plugin.getLocaleManager().sendMessage(p, "proxy.searching", Placeholder.unparsed("server", serverAlias));
            startQueueTimer(p, targetServer);
        }
    }


    private void handleFindLocation(ProxyTeleportRequest request) {
        plugin.debug("Handling FindLocation for " + request.playerUUID() + ". Type: " + request.requestType());

        if ("GROUP_MEMBER".equals(request.requestType())) {
            plugin.debug("Skipping location finding for GROUP_MEMBER request for " + request.playerUUID() + ". Waiting for leader's update.");
            return;
        }

        World worldToSearch = findValidWorld(request.targetWorld());
        if (worldToSearch == null) {
            plugin.getLogger().warning("Invalid proxy RTP request for " + request.playerUUID() + ": No valid world could be determined on this server.");
            plugin.getDatabaseManager().failTeleportRequest(request.playerUUID())
                    .exceptionally(ex -> {
                        plugin.getLogger().severe("Failed to mark request as FAILED for " + request.playerUUID() + ": " + ex.getMessage());
                        return null;
                    });
            return;
        }

        plugin.debug("Determined world to search: " + worldToSearch.getName() + " (Environment: " + worldToSearch.getEnvironment() + ")");
        
        plugin.getRtpService().findSafeLocation(null, worldToSearch, 0, Optional.ofNullable(request.minRadius()), Optional.ofNullable(request.maxRadius()))
                .whenComplete((locationOpt, throwable) -> {
                    if (throwable != null) {
                        plugin.getLogger().severe("Error finding safe location for cross-server request " + request.playerUUID() + ": " + throwable.getMessage());
                        plugin.getDatabaseManager().failTeleportRequest(request.playerUUID())
                                .exceptionally(ex -> {
                                    plugin.getLogger().severe("Failed to mark request as FAILED for " + request.playerUUID() + ": " + ex.getMessage());
                                    return null;
                                });
                    } else if (locationOpt.isPresent()) {
                        plugin.debug("Safe location found for " + request.playerUUID() + ". Updating database with COMPLETE status.");
                        
                        if ("GROUP_LEADER".equals(request.requestType())) {
                            plugin.getDatabaseManager().updateGroupTeleportRequestWithLocation(request.playerUUID(), locationOpt.get())
                                    .exceptionally(ex -> {
                                        plugin.getLogger().severe("Failed to update group teleport location for " + request.playerUUID() + ": " + ex.getMessage());
                                        return null;
                                    });
                        } else {
                            plugin.getDatabaseManager().updateTeleportRequestWithLocation(request.playerUUID(), locationOpt.get())
                                    .exceptionally(ex -> {
                                        plugin.getLogger().severe("Failed to update teleport location for " + request.playerUUID() + ": " + ex.getMessage());
                                        return null;
                                    });
                        }
                    } else {
                        plugin.debug("Failed to find safe location for " + request.playerUUID() + ". Marking as FAILED in DB.");
                        plugin.getDatabaseManager().failTeleportRequest(request.playerUUID())
                                .exceptionally(ex -> {
                                    plugin.getLogger().severe("Failed to mark request as FAILED for " + request.playerUUID() + ": " + ex.getMessage());
                                    return null;
                                });
                    }
                });
    }

    private World findValidWorld(String requestedWorldName) {
        String resolvedWorldName = plugin.getConfigManager().resolveWorldAlias(requestedWorldName);
        if (resolvedWorldName != null && !resolvedWorldName.isEmpty()) {
            World world = Bukkit.getWorld(resolvedWorldName);
            if (world != null && plugin.getRtpService().isRtpEnabled(world)) {
                plugin.debug("Resolved world via alias: " + requestedWorldName + " -> " + resolvedWorldName);
                ensureDimensionSafety(world);
                return world;
            }
        }
        
        if (requestedWorldName != null && !requestedWorldName.isEmpty()) {
            World directWorld = Bukkit.getWorld(requestedWorldName);
            if (directWorld != null && plugin.getRtpService().isRtpEnabled(directWorld)) {
                plugin.debug("Found world directly: " + requestedWorldName);
                ensureDimensionSafety(directWorld);
                return directWorld;
            }
        }
        if (requestedWorldName != null && requestedWorldName.toLowerCase().contains("nether")) {
            World netherWorld = Bukkit.getWorld("world_nether");
            if (netherWorld != null && plugin.getRtpService().isRtpEnabled(netherWorld)) {
                plugin.debug("Resolved nether world: " + requestedWorldName + " -> world_nether");
                ensureDimensionSafety(netherWorld);
                return netherWorld;
            }
        }
        
        if (requestedWorldName != null && requestedWorldName.toLowerCase().contains("end")) {
            World endWorld = Bukkit.getWorld("world_the_end");
            if (endWorld != null && plugin.getRtpService().isRtpEnabled(endWorld)) {
                plugin.debug("Resolved end world: " + requestedWorldName + " -> world_the_end");
                ensureDimensionSafety(endWorld);
                return endWorld;
            }
        }
        
        if (requestedWorldName != null) {
            for (World world : Bukkit.getWorlds()) {
                if (plugin.getRtpService().isRtpEnabled(world)) {
                    if (requestedWorldName.toLowerCase().contains("nether") && world.getEnvironment() == World.Environment.NETHER) {
                        plugin.debug("Found nether environment world: " + world.getName());
                        ensureDimensionSafety(world);
                        return world;
                    }
                    if (requestedWorldName.toLowerCase().contains("end") && world.getEnvironment() == World.Environment.THE_END) {
                        plugin.debug("Found end environment world: " + world.getName());
                        ensureDimensionSafety(world);
                        return world;
                    }
                }
            }
        }
        
        plugin.debug("Failed to resolve world: " + requestedWorldName);
        return null;
    }
    
    private void ensureDimensionSafety(World world) {
        if (world == null) return;
        
        String worldName = world.getName();
        World.Environment environment = world.getEnvironment();
        
        String configuredType = plugin.getConfig().getString("world_types." + worldName);
        
        if (environment == World.Environment.NETHER) {
            if (configuredType == null || !configuredType.equalsIgnoreCase("NETHER")) {
                plugin.getLogger().warning("Cross-server safety check: World '" + worldName + "' is NETHER environment but not configured as world_types." + worldName + ": 'NETHER' in config.yml!");
                plugin.getLogger().warning("This may allow nether roof spawns (Y >= 127). Please add to config.yml:");
                plugin.getLogger().warning("world_types:");
                plugin.getLogger().warning("  " + worldName + ": 'NETHER'");
            } else {
                plugin.debug("Dimension safety validated: " + worldName + " is properly configured as NETHER type (Y < 127 enforced)");
            }
        } else if (environment == World.Environment.THE_END) {
            if (configuredType == null || !configuredType.equalsIgnoreCase("THE_END")) {
                plugin.getLogger().warning("Cross-server safety check: World '" + worldName + "' is THE_END environment but not configured as world_types." + worldName + ": 'THE_END' in config.yml!");
                plugin.getLogger().warning("Please add to config.yml:");
                plugin.getLogger().warning("world_types:");
                plugin.getLogger().warning("  " + worldName + ": 'THE_END'");
            } else {
                plugin.debug("Dimension safety validated: " + worldName + " is properly configured as THE_END type");
            }
        }
    }

    private void handleFinalizedRequest(ProxyTeleportRequest request) {
        Player player = Bukkit.getPlayer(request.playerUUID());
        if (player == null || !player.isOnline()) {
            plugin.debug("Player " + request.playerUUID() + " not online, removing request");
            plugin.getDatabaseManager().removeTeleportRequest(request.playerUUID());
            return;
        }

        cancelQueueTimer(request.playerUUID());
        String serverAlias = plugin.getConfigManager().getProxyServerAlias(request.targetServer());

        if ("COMPLETE".equals(request.status())) {
            plugin.debug("[MySQL Response] Found COMPLETE request for " + player.getName() + ". Sending them to server " + request.targetServer());
            
            plugin.getDatabaseManager().markTeleportRequestAsTransferring(player.getUniqueId())
                    .thenAccept(success -> {
                        if (success) {
                            if (!player.isOnline()) {
                                plugin.debug("Player " + player.getName() + " went offline before transfer, marking as FAILED");
                                plugin.getDatabaseManager().failTeleportRequest(player.getUniqueId());
                                return;
                            }
                            
                            plugin.debug("Marked request as IN_TRANSFER for " + player.getName() + ", now sending to server");
                            
                            try {
                                plugin.getProxyManager().sendPlayerToServer(player, request.targetServer());
                                plugin.debug("Successfully initiated transfer for " + player.getName() + " to " + request.targetServer());
                            } catch (Exception e) {
                                plugin.getLogger().severe("Exception during player transfer for " + player.getName() + ": " + e.getMessage());
                                plugin.getLocaleManager().sendMessage(player, "proxy.transfer_failed");
                                plugin.getDatabaseManager().failTeleportRequest(player.getUniqueId());
                            }
                        } else {
                            plugin.getLogger().warning("Failed to mark request as IN_TRANSFER for " + player.getName() + ", aborting transfer");
                            plugin.getLocaleManager().sendMessage(player, "proxy.transfer_failed");
                            plugin.getDatabaseManager().failTeleportRequest(player.getUniqueId());
                            plugin.getCooldownManager().clearCooldown(player.getUniqueId());
                        }
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger().severe("Error marking request as IN_TRANSFER for " + player.getName() + ": " + throwable.getMessage());
                        if (player.isOnline()) {
                            plugin.getLocaleManager().sendMessage(player, "proxy.transfer_failed");
                        }
                        plugin.getDatabaseManager().failTeleportRequest(player.getUniqueId());
                        plugin.getCooldownManager().clearCooldown(player.getUniqueId());
                        return null;
                    });
            
        } else if ("FAILED".equals(request.status())) {
            plugin.debug("[MySQL Response] Found FAILED request for " + player.getName() + ".");
            plugin.getLocaleManager().sendMessage(player, "proxy.no_location_found_on_server", Placeholder.unparsed("server", serverAlias));
            plugin.getLocaleManager().sendMessage(player, "proxy.cooldown_reset_on_fail");
            plugin.getCooldownManager().clearCooldown(player.getUniqueId());
            plugin.getDatabaseManager().removeTeleportRequest(player.getUniqueId());
        }
    }

    private void startQueueTimer(Player player, String serverName) {
        cancelQueueTimer(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("effects.queue_action_bar.enabled", true)) {
            return;
        }

        String serverAlias = plugin.getConfigManager().getProxyServerAlias(serverName);
        AtomicInteger seconds = new AtomicInteger(0);
        int timeoutSeconds = plugin.getConfig().getInt("proxy.timeout_seconds", 90);
        
        CancellableTask task = plugin.getFoliaScheduler().runTimer(() -> {
            if (!player.isOnline() || seconds.incrementAndGet() > timeoutSeconds) {
                if (player.isOnline()) {
                    plugin.getDatabaseManager().getTeleportRequest(player.getUniqueId()).thenAccept(requestOpt -> {
                        if (requestOpt.isPresent() && ("PENDING".equals(requestOpt.get().status()) || "PROCESSING".equals(requestOpt.get().status()))) {
                            plugin.debug("Cross-server location timeout after " + timeoutSeconds + "s for " + player.getName());
                            plugin.getLocaleManager().sendMessage(player, "proxy.no_location_found_on_server", Placeholder.unparsed("server", serverAlias));
                            plugin.getLocaleManager().sendMessage(player, "proxy.cooldown_reset_on_fail");
                            plugin.getCooldownManager().clearCooldown(player.getUniqueId());
                            plugin.getDatabaseManager().removeTeleportRequest(player.getUniqueId());
                        }
                    });
                }
                cancelQueueTimer(player.getUniqueId());
                return;
            }
            plugin.getEffectsManager().sendQueueActionBar(player, serverAlias, seconds.get());
        }, 0L, 20L);
        activeTimers.put(player.getUniqueId(), task);
    }

    public void cancelQueueTimer(UUID playerUUID) {
        CancellableTask task = activeTimers.remove(playerUUID);
        if (task != null && !task.isCancelled()) {
            task.cancel();
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                plugin.debug("Cancelled queue timer for " + player.getName());
                plugin.getEffectsManager().clearActionBar(player);
            }
        }
    }
}