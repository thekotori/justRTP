package eu.kotori.justRTP.managers;

import eu.kotori.justRTP.JustRTP;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class HologramManager {

    private final JustRTP plugin;
    private FileConfiguration hologramsConfig;
    private FileConfiguration displayEntitiesConfig;
    private final File displayEntitiesFile;
    private final Map<String, HologramInstance> activeHolograms = new ConcurrentHashMap<>();
    private final Map<String, Boolean> hologramCreationLocks = new ConcurrentHashMap<>();
    private final String HOLOGRAM_METADATA_KEY = "justrtp_hologram";

    private static class HologramInstance {
        final List<TextDisplay> displays;
        final AtomicLong particleTick = new AtomicLong(0);

        HologramInstance(List<TextDisplay> displays) {
            this.displays = displays;
        }
    }

    public HologramManager(JustRTP plugin) {
        this.plugin = plugin;
        this.displayEntitiesFile = new File(plugin.getDataFolder(), "display_entities.yml");
    }

    public void initialize() {
        File configFile = new File(plugin.getDataFolder(), "holograms.yml");
        if (!configFile.exists()) {
            plugin.saveResource("holograms.yml", false);
        }
        hologramsConfig = YamlConfiguration.loadConfiguration(configFile);
        loadDisplayEntities();
    }

    private void loadDisplayEntities() {
        try {
            if (!displayEntitiesFile.exists()) {
                displayEntitiesFile.createNewFile();
            }
            displayEntitiesConfig = YamlConfiguration.loadConfiguration(displayEntitiesFile);
            if (displayEntitiesConfig.getConfigurationSection("zones") == null) {
                displayEntitiesConfig.createSection("zones");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create or load display_entities.yml!");
            e.printStackTrace();
        }
    }

    private void saveDisplayEntities() {
        try {
            displayEntitiesConfig.save(displayEntitiesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save display_entities.yml!");
            e.printStackTrace();
        }
    }

    public boolean isHologramActive(String zoneId) {
        return activeHolograms.containsKey(zoneId.toLowerCase());
    }

    public boolean isBeingCreated(String zoneId) {
        return hologramCreationLocks.containsKey(zoneId.toLowerCase());
    }

    public double getDefaultYOffset() {
        return hologramsConfig.getDouble("hologram-settings.y-offset", 2.5);
    }

    public void createOrUpdateHologram(String zoneId, Location location, int viewDistance) {
        if (hologramCreationLocks.putIfAbsent(zoneId.toLowerCase(), true) != null) {
            plugin.debug("Hologram creation/update for " + zoneId + " is already in progress.");
            return;
        }

        plugin.getFoliaScheduler().runAtLocation(location, () -> {
            try {
                removeHologram(zoneId);
                World world = location.getWorld();
                if (world == null) return;

                List<String> lines = hologramsConfig.getStringList("hologram-settings.lines");
                double lineSpacing = hologramsConfig.getDouble("hologram-settings.line-spacing", 0.35);
                float scale = (float) hologramsConfig.getDouble("hologram-settings.scale", 1.0);
                Location textLocation = location.clone();

                List<TextDisplay> textDisplays = new ArrayList<>();
                List<String> entityUuids = new ArrayList<>();

                for (String line : lines) {
                    if (line.isEmpty()) {
                        textLocation.subtract(0, lineSpacing, 0);
                        continue;
                    }
                    TextDisplay display = world.spawn(textLocation, TextDisplay.class, d -> {
                        Transformation transformation = d.getTransformation();
                        transformation.getScale().set(new Vector3f(scale, scale, scale));
                        d.setTransformation(transformation);
                        d.setBillboard(Display.Billboard.CENTER);
                        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        d.setShadowed(true);
                        d.setPersistent(false);

                        d.setViewRange(viewDistance);
                        d.text(MiniMessage.miniMessage().deserialize(line, Placeholder.unparsed("zone_id", zoneId), Placeholder.unparsed("time", "")));
                        d.setMetadata(HOLOGRAM_METADATA_KEY, new FixedMetadataValue(plugin, zoneId));
                    });
                    textDisplays.add(display);
                    entityUuids.add(display.getUniqueId().toString());
                    textLocation.subtract(0, lineSpacing, 0);
                }
                activeHolograms.put(zoneId.toLowerCase(), new HologramInstance(textDisplays));
                displayEntitiesConfig.set("zones." + zoneId.toLowerCase(), entityUuids);
                saveDisplayEntities();
            } finally {
                hologramCreationLocks.remove(zoneId.toLowerCase());
            }
        });
    }

    public void updateHologramTime(String zoneId, int time) {
        HologramInstance instance = activeHolograms.get(zoneId.toLowerCase());
        if (instance == null || instance.displays.isEmpty()) return;

        boolean anyValid = instance.displays.stream().anyMatch(d -> d != null && d.isValid() && !d.isDead());
        if (!anyValid) {
            plugin.debug("All hologram displays for zone " + zoneId + " are invalid/dead. Removing instance.");
            activeHolograms.remove(zoneId.toLowerCase());
            return;
        }

        Location hologramCenter = instance.displays.get(0).getLocation();

        List<String> lines = hologramsConfig.getStringList("hologram-settings.lines");
        int displayIndex = 0;
        for (String line : lines) {
            if (displayIndex >= instance.displays.size()) break;
            if (!line.isEmpty()) {
                TextDisplay display = instance.displays.get(displayIndex);
                if (display != null && display.isValid()) {
                    display.text(MiniMessage.miniMessage().deserialize(line,
                            Placeholder.unparsed("zone_id", zoneId),
                            Placeholder.unparsed("time", String.valueOf(time))
                    ));
                }
                displayIndex++;
            }
        }
        spawnParticles(hologramCenter, instance);
    }

    private void spawnParticles(Location center, HologramInstance instance) {
        ConfigurationSection particleSection = hologramsConfig.getConfigurationSection("hologram-settings.particles");
        if (particleSection == null || !particleSection.getBoolean("enabled")) return;

        try {
            Particle particle = Particle.valueOf(particleSection.getString("particle", "ENCHANTMENT_TABLE").toUpperCase());
            int count = particleSection.getInt("count", 2);
            double radius = particleSection.getDouble("radius", 0.8);
            double angle = (instance.particleTick.getAndIncrement() * 0.1) % (2 * Math.PI);

            for (int i = 0; i < count; i++) {
                double currentAngle = angle + (2 * Math.PI * i / count);
                double x = center.getX() + radius * Math.cos(currentAngle);
                double z = center.getZ() + radius * Math.sin(currentAngle);
                center.getWorld().spawnParticle(particle, x, center.getY(), z, 1, 0, 0, 0, 0);
            }
        } catch (IllegalArgumentException e) {
        }
    }

    public void removeHologram(String zoneId) {
        HologramInstance instance = activeHolograms.remove(zoneId.toLowerCase());
        if (instance != null) {
            instance.displays.forEach(display -> {
                if (display != null && !display.isDead()) {
                    display.remove();
                }
            });
        }

        List<String> uuidStrings = displayEntitiesConfig.getStringList("zones." + zoneId.toLowerCase());
        if (!uuidStrings.isEmpty()) {
            for (String uuidString : uuidStrings) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    Entity entity = Bukkit.getEntity(uuid);
                    if (entity instanceof TextDisplay && entity.isValid()) {
                        entity.remove();
                    }
                } catch (IllegalArgumentException e) {
                }
            }
        }

        displayEntitiesConfig.set("zones." + zoneId.toLowerCase(), null);
        saveDisplayEntities();
    }

    public void cleanupAllHolograms() {
        plugin.debug("Cleaning up all JustRTP holograms from all worlds...");

        activeHolograms.values().forEach(instance -> instance.displays.forEach(Entity::remove));
        activeHolograms.clear();

        ConfigurationSection zonesSection = displayEntitiesConfig.getConfigurationSection("zones");
        if (zonesSection != null) {
            for (String zoneId : zonesSection.getKeys(false)) {
                List<String> uuidStrings = zonesSection.getStringList(zoneId);
                for (String uuidString : uuidStrings) {
                    try {
                        UUID uuid = UUID.fromString(uuidString);
                        Entity entity = Bukkit.getEntity(uuid);
                        if (entity instanceof TextDisplay && entity.isValid()) {
                            entity.remove();
                            plugin.debug("Removed orphaned hologram entity by UUID: " + uuid);
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }

        displayEntitiesConfig.set("zones", null);
        saveDisplayEntities();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.hasMetadata(HOLOGRAM_METADATA_KEY)) {
                    entity.remove();
                    plugin.debug("Removed orphaned hologram entity by metadata: " + entity.getUniqueId());
                }
            }
        }
        plugin.debug("Hologram cleanup complete.");
    }
}