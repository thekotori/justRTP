package eu.kotori.justRTP.handlers;

import eu.kotori.justRTP.JustRTP;
import eu.kotori.justRTP.commands.RTPCommand;
import io.papermc.lib.PaperLib;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class PlayerListener implements Listener {
    private final JustRTP plugin;
    public PlayerListener(JustRTP plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (plugin.getDelayManager().isDelayed(player.getUniqueId())) {
            if (event.getFrom().distanceSquared(event.getTo()) > 0.001) {
                if (plugin.getConfig().getBoolean("delay_settings.cancel_on_move", true)) {
                    plugin.getDelayManager().cancelDelay(player);
                }
            }
        }

        if (event.hasChangedBlock()) {
            plugin.getRtpZoneManager().handlePlayerMove(player, event.getTo());
        }
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getConfig().getBoolean("first_join_rtp.enabled") && !player.hasPlayedBefore()) {
            handleFirstJoinRtp(player);
        }

        if (plugin.getConfigManager().getProxyEnabled()) {
            handleProxyJoin(player);
        }

        plugin.getFoliaScheduler().runAtEntityLater(player, () -> {
            if (player.isOnline()) {
                plugin.getRtpZoneManager().handlePlayerMove(player, player.getLocation());
            }
        }, 20L);
    }

    private void handleProxyJoin(Player player) {
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) return;
        plugin.debug("Player " + player.getName() + " joined. Checking for pending proxy RTP requests.");

        plugin.getDatabaseManager().getTeleportRequest(player.getUniqueId()).thenAccept(requestOpt -> {
            requestOpt.ifPresent(request -> {
                plugin.debug("Found request for " + player.getName() + " with status: " + request.status() + " and type: " + request.requestType());

                if ("COMPLETE".equals(request.status()) && request.location() != null) {
                    if (!player.isOnline()) {
                        plugin.getDatabaseManager().removeTeleportRequest(player.getUniqueId());
                        return;
                    }

                    CompletableFuture<Location> locationFuture;
                    if (request.requestType().startsWith("GROUP")) {
                        plugin.debug("Player is part of a group teleport. Spreading around target.");
                        locationFuture = getSpreadLocation(request.location());
                    } else {
                        locationFuture = CompletableFuture.completedFuture(request.location());
                    }

                    locationFuture.thenAccept(targetLocation -> {
                        if (targetLocation != null && player.isOnline()) {
                            PaperLib.teleportAsync(player, targetLocation).thenAccept(success -> {
                                if (success) {
                                    plugin.getEffectsManager().applyPostTeleportEffects(player);
                                }
                                plugin.getDatabaseManager().removeTeleportRequest(player.getUniqueId());
                            });
                        } else {
                            plugin.getDatabaseManager().removeTeleportRequest(player.getUniqueId());
                        }
                    });
                } else {
                    plugin.getDatabaseManager().removeTeleportRequest(player.getUniqueId());
                }
            });
        });
    }

    private CompletableFuture<Location> getSpreadLocation(Location center) {
        CompletableFuture<Location> future = new CompletableFuture<>();
        double minSpread = plugin.getConfig().getDouble("zone_teleport_settings.min_spread_distance", 5);
        double maxSpread = plugin.getConfig().getDouble("zone_teleport_settings.max_spread_distance", 15);
        double angle = ThreadLocalRandom.current().nextDouble(2 * Math.PI);
        double spread = ThreadLocalRandom.current().nextDouble(minSpread, maxSpread);
        double offsetX = spread * Math.cos(angle);
        double offsetZ = spread * Math.sin(angle);
        Location target = center.clone().add(offsetX, 0, offsetZ);

        plugin.getFoliaScheduler().runAtLocation(target, () -> {
            Location finalLoc = target.getWorld().getHighestBlockAt(target).getLocation().add(0.5, 1.5, 0.5);
            future.complete(finalLoc);
        });

        return future;
    }


    private void handleFirstJoinRtp(Player player) {
        plugin.getFoliaScheduler().runLater(() -> {
            if (!player.isOnline()) return;
            String worldName = plugin.getConfig().getString("first_join_rtp.target_world");
            World world = plugin.getServer().getWorld(worldName);
            if (world != null) {
                plugin.getTeleportQueueManager().requestTeleport(player, world, java.util.Optional.empty(), java.util.Optional.empty());
            } else {
                plugin.getLogger().warning("Invalid world specified for first_join_rtp: " + worldName);
            }
        }, 20L);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!plugin.getConfig().getBoolean("delay_settings.cancel_on_combat", true)) return;
        if (event.getEntity() instanceof Player victim && plugin.getDelayManager().isDelayed(victim.getUniqueId())) {
            plugin.getDelayManager().cancelDelay(victim);
        }
        if (event instanceof EntityDamageByEntityEvent damageByEntityEvent) {
            if (damageByEntityEvent.getDamager() instanceof Player damager && plugin.getDelayManager().isDelayed(damager.getUniqueId())) {
                plugin.getDelayManager().cancelDelay(damager);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getDelayManager().cancelDelay(player);
        plugin.getEffectsManager().removeBossBar(player);
        plugin.getZoneSetupManager().cancelSetup(player);
        plugin.getRtpZoneManager().handlePlayerQuit(player);
        plugin.getCrossServerManager().cancelQueueTimer(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfig().getBoolean("respawn_rtp.enabled", false)) return;
        Player player = event.getPlayer();
        World world = player.getWorld();
        List<String> enabledWorlds = plugin.getConfig().getStringList("respawn_rtp.worlds");

        if (enabledWorlds.isEmpty() || enabledWorlds.contains(world.getName())) {
            plugin.getFoliaScheduler().runAtEntityLater(player, () -> {
                if (player.isOnline()) {
                    RTPCommand rtpCommand = (RTPCommand) plugin.getCommand("justrtp").getExecutor();
                    rtpCommand.processRtpRequest(player, player, new String[0], false);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (plugin.getZoneSetupManager().isWand(event.getItem())) {
            event.setCancelled(true);
            plugin.getZoneSetupManager().handleWandInteraction(event);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (plugin.getZoneSetupManager().isWand(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (plugin.getZoneSetupManager().isWand(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        if (plugin.getZoneSetupManager().isInSetupMode(event.getPlayer())) {
            event.setCancelled(true);
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            plugin.getFoliaScheduler().runAtEntity(event.getPlayer(), () ->
                    plugin.getZoneSetupManager().handleChatInput(event.getPlayer(), message));
        }
    }
}