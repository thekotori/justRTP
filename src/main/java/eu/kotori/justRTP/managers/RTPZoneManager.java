package eu.kotori.justRTP.managers;

import eu.kotori.justRTP.JustRTP;
import eu.kotori.justRTP.utils.RTPZone;
import eu.kotori.justRTP.utils.task.CancellableTask;
import io.papermc.lib.PaperLib;
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
import java.util.concurrent.ThreadLocalRandom;
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
        final int[] countdown = {interval + 1};

        CancellableTask task = plugin.getFoliaScheduler().runTimerAtLocation(zoneCenter, () -> {
            try {
                countdown[0]--;

                List<Player> playersInZone = getPlayersInZone(zone.getId());

                if (countdown[0] <= 0) {
                    plugin.getHologramManager().updateHologramTime(zone.getId(), 0);
                    if (!playersInZone.isEmpty()) {
                        teleportPlayersInZone(playersInZone, zone);
                    }
                    countdown[0] = interval + 1;
                    return;
                }

                plugin.getHologramManager().updateHologramTime(zone.getId(), countdown[0]);

                for (Player player : playersInZone) {
                    if (!isIgnoring(player)) {
                        plugin.getFoliaScheduler().runAtEntity(player, () -> updateWaitingEffects(player, zone, countdown[0]));
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
                .filter(p -> !isIgnoring(p))
                .collect(Collectors.toList());

        if (teleportCandidates.isEmpty()) return;

        Set<UUID> playersInThisZone = zonePlayersMap.get(zone.getId().toLowerCase());
        if (playersInThisZone != null) {
            teleportCandidates.forEach(p -> {
                UUID playerUUID = p.getUniqueId();
                playerZoneMap.remove(playerUUID);
                playersInThisZone.remove(playerUUID);
                recentlyTeleported.add(playerUUID);
                plugin.getFoliaScheduler().runLater(() -> recentlyTeleported.remove(playerUUID), 100L);

                p.clearTitle();
                plugin.getEffectsManager().clearActionBar(p);
                plugin.getEffectsManager().applyEffects(p, getZoneEffects(zone, "teleport"));
            });
        }

        World targetWorld = Bukkit.getWorld(zone.getTarget());
        if (targetWorld != null) {
            handleLocalZoneTeleport(teleportCandidates, zone, targetWorld);
        } else if (plugin.getConfigManager().getProxyEnabled()) {
            handleProxyZoneTeleport(teleportCandidates, zone);
        }
    }

    private void handleProxyZoneTeleport(List<Player> players, RTPZone zone) {
        plugin.getCrossServerManager().sendGroupFindLocationRequest(players, zone.getTarget(), Optional.of(zone.getMinRadius()), Optional.of(zone.getMaxRadius()));
    }

    private void handleLocalZoneTeleport(List<Player> players, RTPZone zone, World targetWorld) {
        plugin.getRtpService()
                .findSafeLocation(null, targetWorld, 25, Optional.of(zone.getMinRadius()), Optional.of(zone.getMaxRadius()))
                .thenAccept(centerOpt -> {
                    if (centerOpt.isPresent()) {
                        spreadAndTeleportPlayers(players, zone, centerOpt.get());
                    } else {
                        plugin.getLogger().warning("Could not find a central safe spot for RTP Zone '" + zone.getId() + "'. Teleport aborted.");
                        players.forEach(p -> plugin.getLocaleManager().sendMessage(p, "teleport.no_location_found"));
                    }
                });
    }

    private void spreadAndTeleportPlayers(List<Player> players, RTPZone zone, Location center) {
        List<CompletableFuture<Optional<Location>>> locationFutures = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            locationFutures.add(findSafeSpreadLocation(center, zone.getMinSpreadDistance(), zone.getMaxSpreadDistance(), 5));
        }

        CompletableFuture.allOf(locationFutures.toArray(new CompletableFuture[0])).thenAccept(v -> {
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                Optional<Location> locationOpt = locationFutures.get(i).join();
                locationOpt.ifPresent(safeSpot -> {
                    PaperLib.teleportAsync(player, safeSpot).thenAccept(success -> {
                        if (success) {
                            plugin.getEffectsManager().applyPostTeleportEffects(player);
                        }
                    });
                });
            }
        });
    }

    private CompletableFuture<Optional<Location>> findSafeSpreadLocation(Location center, double minRadius, double maxRadius, int attempts) {
        if (attempts <= 0) {
            plugin.getLogger().warning("Could not find a safe spread location near " + center.toString() + " after multiple attempts. Using center as fallback.");
            return CompletableFuture.completedFuture(Optional.of(center.getWorld().getHighestBlockAt(center).getLocation().add(0.5, 1.5, 0.5)));
        }

        double angle = ThreadLocalRandom.current().nextDouble(2 * Math.PI);
        double spread = ThreadLocalRandom.current().nextDouble(minRadius, maxRadius);
        double offsetX = spread * Math.cos(angle);
        double offsetZ = spread * Math.sin(angle);
        Location target = center.clone().add(offsetX, 0, offsetZ);

        return PaperLib.getChunkAtAsync(target.getWorld(), target.getBlockX() >> 4, target.getBlockZ() >> 4, true).thenCompose(chunk -> {
            if (chunk == null) {
                return findSafeSpreadLocation(center, minRadius, maxRadius, attempts - 1);
            }
            Location finalLoc = target.getWorld().getHighestBlockAt(target).getLocation();
            if (plugin.getRtpService().isSafeForSpread(finalLoc)) {
                return CompletableFuture.completedFuture(Optional.of(finalLoc.add(0.5, 1.5, 0.5)));
            } else {
                return findSafeSpreadLocation(center, minRadius, maxRadius, attempts - 1);
            }
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

    public boolean zoneExists(String id) {
        return zones.containsKey(id.toLowerCase());
    }

    public Set<String> getZoneIds() {
        return zones.keySet();
    }
}