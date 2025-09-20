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
        plugin.getDatabaseManager().createTeleportRequest(player.getUniqueId(), plugin.getConfigManager().getProxyThisServerName(), targetServer, argsString, targetWorldName, minRadius, maxRadius, "INDIVIDUAL");

        startQueueTimer(player, targetServer);
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
            plugin.getDatabaseManager().failTeleportRequest(request.playerUUID());
            return;
        }

        plugin.debug("Determined world to search: " + worldToSearch.getName());
        int attempts = plugin.getConfig().getInt("settings.attempts", 25);
        plugin.getRtpService().findSafeLocation(null, worldToSearch, attempts, Optional.ofNullable(request.minRadius()), Optional.ofNullable(request.maxRadius()))
                .thenAccept(locationOpt -> {
                    if (locationOpt.isPresent()) {
                        plugin.debug("Safe location found for " + request.playerUUID() + ". Updating database.");
                        if ("GROUP_LEADER".equals(request.requestType())) {
                            plugin.getDatabaseManager().updateGroupTeleportRequestWithLocation(request.playerUUID(), locationOpt.get());
                        } else {
                            plugin.getDatabaseManager().updateTeleportRequestWithLocation(request.playerUUID(), locationOpt.get());
                        }
                    } else {
                        plugin.debug("Failed to find safe location for " + request.playerUUID() + ". Marking as failed in DB.");
                        plugin.getDatabaseManager().failTeleportRequest(request.playerUUID());
                    }
                });
    }

    private World findValidWorld(String requestedWorldName) {
        String resolvedWorldName = plugin.getConfigManager().resolveWorldAlias(requestedWorldName);
        if (resolvedWorldName != null && !resolvedWorldName.isEmpty()) {
            World world = Bukkit.getWorld(resolvedWorldName);
            if (world != null && plugin.getRtpService().isRtpEnabled(world)) {
                return world;
            }
        }
        
        if (requestedWorldName != null && !requestedWorldName.isEmpty()) {
            World directWorld = Bukkit.getWorld(requestedWorldName);
            if (directWorld != null && plugin.getRtpService().isRtpEnabled(directWorld)) {
                return directWorld;
            }
        }
        
        return null;
    }

    private void handleFinalizedRequest(ProxyTeleportRequest request) {
        Player player = Bukkit.getPlayer(request.playerUUID());
        if (player == null || !player.isOnline()) {
            plugin.getDatabaseManager().removeTeleportRequest(request.playerUUID());
            return;
        }

        cancelQueueTimer(request.playerUUID());
        String serverAlias = plugin.getConfigManager().getProxyServerAlias(request.targetServer());

        if ("COMPLETE".equals(request.status())) {
            plugin.debug("[MySQL Response] Found COMPLETE request for " + player.getName() + ". Sending them to server " + request.targetServer());
            plugin.getProxyManager().sendPlayerToServer(player, request.targetServer());
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
        CancellableTask task = plugin.getFoliaScheduler().runTimer(() -> {
            if (!player.isOnline() || seconds.incrementAndGet() > 30) {
                if (player.isOnline()) {
                    plugin.getDatabaseManager().getTeleportRequest(player.getUniqueId()).thenAccept(requestOpt -> {
                        if (requestOpt.isPresent() && ("PENDING".equals(requestOpt.get().status()) || "PROCESSING".equals(requestOpt.get().status()))) {
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