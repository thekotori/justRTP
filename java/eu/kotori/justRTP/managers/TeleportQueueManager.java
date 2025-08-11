package eu.kotori.justRTP.managers;

import eu.kotori.justRTP.JustRTP;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TeleportQueueManager {
    private record TeleportRequest(Player player, World world, Optional<Integer> minRadius, Optional<Integer> maxRadius, CompletableFuture<Boolean> future) {}
    private final JustRTP plugin;
    private final Queue<TeleportRequest> queue = new LinkedList<>();

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
            for (int i = 0; i < finalBatchSize && !queue.isEmpty(); i++) {
                TeleportRequest request = queue.poll();
                if (request.player() != null && request.player().isOnline()) {
                    plugin.getRtpService().findSafeLocation(request.player(), request.world(), plugin.getConfig().getInt("settings.attempts", 25), request.minRadius(), request.maxRadius())
                            .thenAccept(locationOpt -> {
                                if (locationOpt.isPresent()) {
                                    plugin.getRtpService().teleportPlayer(request.player(), locationOpt.get());
                                    request.future().complete(true);
                                } else {
                                    plugin.getLocaleManager().sendMessage(request.player(), "teleport.no_location_found");
                                    request.future().complete(false);
                                }
                            });
                } else {
                    request.future().complete(false);
                }
            }
        }, 1L, rate);
    }

    public CompletableFuture<Boolean> requestTeleport(Player player, World world, Optional<Integer> minRadius, Optional<Integer> maxRadius) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        boolean useQueue = plugin.getConfig().getBoolean("performance.use_teleport_queue", true);
        if (useQueue) {
            queue.add(new TeleportRequest(player, world, minRadius, maxRadius, future));
            plugin.getEffectsManager().applyEffects(player, plugin.getConfig().getConfigurationSection("effects.in_queue_action_bar"));
        } else {
            plugin.getRtpService().findSafeLocation(player, world, plugin.getConfig().getInt("settings.attempts", 25), minRadius, maxRadius)
                    .thenAccept(locationOpt -> {
                        if (locationOpt.isPresent()) {
                            plugin.getRtpService().teleportPlayer(player, locationOpt.get());
                            future.complete(true);
                        } else {
                            plugin.getLocaleManager().sendMessage(player, "teleport.no_location_found");
                            future.complete(false);
                        }
                    });
        }
        return future;
    }

    public void cancelRequest(Player player) {
        if (player == null) return;
        UUID playerUUID = player.getUniqueId();
        queue.removeIf(request -> {
            if (request.player() != null && request.player().getUniqueId().equals(playerUUID)) {
                request.future().complete(false);
                return true;
            }
            return false;
        });
    }
}