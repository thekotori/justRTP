package eu.kotori.justRTP.managers;

import eu.kotori.justRTP.JustRTP;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class TeleportQueueManager {
    private record TeleportRequest(Player player, World world, Optional<Integer> minRadius, Optional<Integer> maxRadius, CompletableFuture<Boolean> future, long timestamp) {}
    private final JustRTP plugin;
    private final ConcurrentLinkedQueue<TeleportRequest> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<UUID, AtomicBoolean> processingPlayers = new ConcurrentHashMap<>();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    public TeleportQueueManager(JustRTP plugin) {
        this.plugin = plugin;
        start();
    }

    public void reload() {
        start();
    }

    private void start() {
        boolean useQueue = plugin.getConfig().getBoolean("performance.use_teleport_queue", true);
        if (!useQueue) return;
        long rate = 20L / plugin.getConfig().getLong("performance.queue_processing_rate", 5);
        if (rate <= 0) rate = 1L;
        int batchSize = plugin.getConfig().getInt("performance.queue_batch_size", 1);
        if (batchSize <= 0) batchSize = 1;

        final int finalBatchSize = batchSize;
        plugin.getFoliaScheduler().runTimer(() -> {
            if (!isProcessing.compareAndSet(false, true)) {
                plugin.debug("Queue processing already in progress, skipping this tick");
                return;
            }
            
            try {
                for (int i = 0; i < finalBatchSize && !queue.isEmpty(); i++) {
                    TeleportRequest request = queue.poll();
                    if (request == null) break;
                    
                    Player player = request.player();
                    UUID playerUUID = player.getUniqueId();
                    
                    AtomicBoolean processing = processingPlayers.computeIfAbsent(playerUUID, k -> new AtomicBoolean(false));
                    if (!processing.compareAndSet(false, true)) {
                        plugin.debug("Player " + player.getName() + " is already being processed, skipping duplicate request");
                        request.future().complete(false);
                        continue;
                    }
                    
                    if (!player.isOnline()) {
                        plugin.debug("Player " + player.getName() + " went offline before processing");
                        processingPlayers.remove(playerUUID);
                        request.future().complete(false);
                        continue;
                    }
                    
                    long currentTime = System.currentTimeMillis();
                    if ((currentTime - request.timestamp()) > 60000) {
                        plugin.debug("Teleport request for " + player.getName() + " timed out (>60s in queue)");
                        processingPlayers.remove(playerUUID);
                        plugin.getLocaleManager().sendMessage(player, "teleport.no_location_found");
                        request.future().complete(false);
                        continue;
                    }
                    
                    plugin.getRtpService().findSafeLocation(player, request.world(), 0, request.minRadius(), request.maxRadius())
                            .whenComplete((locationOpt, throwable) -> {
                                try {
                                    if (throwable != null) {
                                        plugin.getLogger().severe("Error finding safe location for " + player.getName() + ": " + throwable.getMessage());
                                        plugin.getLocaleManager().sendMessage(player, "teleport.no_location_found");
                                        request.future().complete(false);
                                    } else if (locationOpt.isPresent()) {
                                        if (player.isOnline()) {
                                            plugin.getRtpService().teleportPlayer(player, locationOpt.get());
                                            request.future().complete(true);
                                        } else {
                                            plugin.debug("Player " + player.getName() + " went offline before teleport");
                                            request.future().complete(false);
                                        }
                                    } else {
                                        plugin.getLocaleManager().sendMessage(player, "teleport.no_location_found");
                                        request.future().complete(false);
                                    }
                                } finally {
                                    processingPlayers.remove(playerUUID);
                                }
                            });
                }
            } finally {
                isProcessing.set(false);
            }
        }, 1L, rate);
    }

    public CompletableFuture<Boolean> requestTeleport(Player player, World world, Optional<Integer> minRadius, Optional<Integer> maxRadius) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        UUID playerUUID = player.getUniqueId();
        
        AtomicBoolean processing = processingPlayers.get(playerUUID);
        if (processing != null && processing.get()) {
            plugin.debug("Player " + player.getName() + " already has a teleport request in progress");
            plugin.getLocaleManager().sendMessage(player, "teleport.already_in_progress");
            future.complete(false);
            return future;
        }
        
        boolean useQueue = plugin.getConfig().getBoolean("performance.use_teleport_queue", true);
        if (useQueue) {
            boolean alreadyInQueue = queue.stream()
                    .anyMatch(req -> req.player().getUniqueId().equals(playerUUID));
            
            if (alreadyInQueue) {
                plugin.debug("Player " + player.getName() + " already has a teleport request in queue");
                plugin.getLocaleManager().sendMessage(player, "teleport.already_in_progress");
                future.complete(false);
                return future;
            }
            
            long timestamp = System.currentTimeMillis();
            queue.add(new TeleportRequest(player, world, minRadius, maxRadius, future, timestamp));
            plugin.getEffectsManager().applyEffects(player, plugin.getConfig().getConfigurationSection("effects.in_queue_action_bar"));
            plugin.debug("Added teleport request to queue for " + player.getName() + " (queue size: " + queue.size() + ")");
        } else {
            AtomicBoolean directProcessing = processingPlayers.computeIfAbsent(playerUUID, k -> new AtomicBoolean(false));
            if (!directProcessing.compareAndSet(false, true)) {
                plugin.debug("Player " + player.getName() + " already has a direct teleport in progress");
                plugin.getLocaleManager().sendMessage(player, "teleport.already_in_progress");
                future.complete(false);
                return future;
            }
            
            plugin.getRtpService().findSafeLocation(player, world, 0, minRadius, maxRadius)
                    .whenComplete((locationOpt, throwable) -> {
                        try {
                            if (throwable != null) {
                                plugin.getLogger().severe("Error finding safe location for " + player.getName() + ": " + throwable.getMessage());
                                plugin.getLocaleManager().sendMessage(player, "teleport.no_location_found");
                                future.complete(false);
                            } else if (locationOpt.isPresent()) {
                                if (player.isOnline()) {
                                    plugin.getRtpService().teleportPlayer(player, locationOpt.get());
                                    future.complete(true);
                                } else {
                                    future.complete(false);
                                }
                            } else {
                                plugin.getLocaleManager().sendMessage(player, "teleport.no_location_found");
                                future.complete(false);
                            }
                        } finally {
                            processingPlayers.remove(playerUUID);
                        }
                    });
        }
        return future;
    }

    public void cancelRequest(Player player) {
        if (player == null) return;
        UUID playerUUID = player.getUniqueId();
        
        AtomicBoolean processing = processingPlayers.remove(playerUUID);
        if (processing != null && processing.get()) {
            plugin.debug("Cancelled in-progress teleport for " + player.getName());
        }
        
        int removed = 0;
        boolean moreToRemove = true;
        while (moreToRemove) {
            moreToRemove = queue.removeIf(request -> {
                if (request.player() != null && request.player().getUniqueId().equals(playerUUID)) {
                    request.future().complete(false);
                    return true;
                }
                return false;
            });
            if (moreToRemove) removed++;
        }
        
        if (removed > 0) {
            plugin.debug("Cancelled " + removed + " queued teleport request(s) for " + player.getName());
        }
    }
    
    public int getQueueSize() {
        return queue.size();
    }
    
    public int getProcessingCount() {
        return (int) processingPlayers.values().stream()
                .filter(AtomicBoolean::get)
                .count();
    }
    
    public boolean isPlayerInProgress(UUID playerUUID) {
        AtomicBoolean processing = processingPlayers.get(playerUUID);
        if (processing != null && processing.get()) {
            return true;
        }
        return queue.stream().anyMatch(req -> req.player().getUniqueId().equals(playerUUID));
    }
}