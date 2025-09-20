package eu.kotori.justRTP.managers;

import eu.kotori.justRTP.JustRTP;
import eu.kotori.justRTP.utils.Cuboid;
import eu.kotori.justRTP.utils.RTPZone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ZoneSetupManager {

    private enum SetupStep {
        AWAITING_POS1, AWAITING_POS2, AWAITING_TARGET, AWAITING_MIN_RADIUS, AWAITING_MAX_RADIUS, AWAITING_INTERVAL, AWAITING_VIEW_DISTANCE
    }

    private final JustRTP plugin;
    private final Map<UUID, ZoneBuilder> setupSessions = new ConcurrentHashMap<>();
    private final ItemStack wand;

    public ZoneSetupManager(JustRTP plugin) {
        this.plugin = plugin;
        this.wand = createWand();
    }

    private ItemStack createWand() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        MiniMessage mm = MiniMessage.miniMessage();
        meta.displayName(mm.deserialize("<gradient:#FF8C00:#FFD700>RTP Zone Wand</gradient>"));
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize("<gray>Left-Click: <yellow>Set Position 1</yellow></gray>"));
        lore.add(mm.deserialize("<gray>Right-Click: <yellow>Set Position 2</yellow></gray>"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(plugin.getCommandManager().getWandKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(plugin.getCommandManager().getWandKey(), PersistentDataType.BYTE);
    }

    public boolean isInSetupMode(Player player) {
        return setupSessions.containsKey(player.getUniqueId());
    }

    public void startSetup(Player player, String zoneId) {
        if (isInSetupMode(player)) {
            plugin.getLocaleManager().sendMessage(player, "zone.error.already_in_setup");
            return;
        }

        if (plugin.getRtpZoneManager().zoneExists(zoneId)) {
            plugin.getLocaleManager().sendMessage(player, "zone.error.already_exists", Placeholder.unparsed("id", zoneId));
            return;
        }

        ZoneBuilder builder = new ZoneBuilder(zoneId, player.getWorld().getName());
        setupSessions.put(player.getUniqueId(), builder);
        player.getInventory().addItem(wand);
        plugin.getLocaleManager().sendMessage(player, "zone.setup.started", Placeholder.unparsed("id", zoneId));
        plugin.getLocaleManager().sendMessage(player, "zone.setup.pos1_prompt");
    }

    public void cancelSetup(Player player) {
        if (setupSessions.remove(player.getUniqueId()) != null) {
            player.getInventory().remove(wand);
            plugin.getLocaleManager().sendMessage(player, "zone.setup.cancelled");
        }
    }

    public void handleWandInteraction(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isInSetupMode(player)) return;
        if (event.getClickedBlock() == null) return;

        ZoneBuilder builder = setupSessions.get(player.getUniqueId());
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            builder.pos1 = event.getClickedBlock().getLocation();
            plugin.getLocaleManager().sendMessage(player, "zone.setup.pos1_set",
                    Placeholder.unparsed("coords", formatLocation(builder.pos1)));
            if (builder.step == SetupStep.AWAITING_POS1) {
                builder.step = SetupStep.AWAITING_POS2;
                plugin.getLocaleManager().sendMessage(player, "zone.setup.pos2_prompt");
            }
            checkPositionsAndProceed(player, builder);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            builder.pos2 = event.getClickedBlock().getLocation();
            plugin.getLocaleManager().sendMessage(player, "zone.setup.pos2_set",
                    Placeholder.unparsed("coords", formatLocation(builder.pos2)));
            checkPositionsAndProceed(player, builder);
        }
    }

    private void checkPositionsAndProceed(Player player, ZoneBuilder builder) {
        if (builder.pos1 != null && builder.pos2 != null) {
            if (builder.step == SetupStep.AWAITING_POS1 || builder.step == SetupStep.AWAITING_POS2) {
                builder.step = SetupStep.AWAITING_TARGET;
                plugin.getLocaleManager().sendMessage(player, "zone.setup.target_prompt");
            }
        }
    }

    public void handleChatInput(Player player, String input) {
        if (!isInSetupMode(player)) return;

        if (input.equalsIgnoreCase("cancel")) {
            cancelSetup(player);
            return;
        }

        ZoneBuilder builder = setupSessions.get(player.getUniqueId());
        switch (builder.step) {
            case AWAITING_POS1:
            case AWAITING_POS2:
                plugin.getLocaleManager().sendMessage(player, "zone.setup.position_first");
                break;
            case AWAITING_TARGET:
                builder.target = input;
                builder.step = SetupStep.AWAITING_MIN_RADIUS;
                plugin.getLocaleManager().sendMessage(player, "zone.setup.target_set", Placeholder.unparsed("target", input));
                plugin.getLocaleManager().sendMessage(player, "zone.setup.min_radius_prompt");
                break;
            case AWAITING_MIN_RADIUS:
                try {
                    int radius = Integer.parseInt(input);
                    if (radius < 0) {
                        plugin.getLocaleManager().sendMessage(player, "command.invalid_radius");
                        return;
                    }
                    builder.minRadius = radius;
                    builder.step = SetupStep.AWAITING_MAX_RADIUS;
                    plugin.getLocaleManager().sendMessage(player, "zone.setup.min_radius_set", Placeholder.unparsed("radius", input));
                    plugin.getLocaleManager().sendMessage(player, "zone.setup.max_radius_prompt");
                } catch (NumberFormatException e) {
                    plugin.getLocaleManager().sendMessage(player, "zone.error.invalid_number");
                }
                break;
            case AWAITING_MAX_RADIUS:
                try {
                    int radius = Integer.parseInt(input);
                    if (radius <= builder.minRadius) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize("<prefix> <red>Max radius must be greater than the minimum radius of " + builder.minRadius + ".</red>", Placeholder.unparsed("prefix", plugin.getLocaleManager().getRawMessage("prefix"))));
                        return;
                    }
                    builder.maxRadius = radius;
                    builder.step = SetupStep.AWAITING_INTERVAL;
                    plugin.getLocaleManager().sendMessage(player, "zone.setup.max_radius_set", Placeholder.unparsed("radius", input));
                    plugin.getLocaleManager().sendMessage(player, "zone.setup.interval_prompt");
                } catch (NumberFormatException e) {
                    plugin.getLocaleManager().sendMessage(player, "zone.error.invalid_number");
                }
                break;
            case AWAITING_INTERVAL:
                try {
                    int interval = Integer.parseInt(input);
                    if (interval <= 0) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize("<prefix> <red>Interval must be a positive number.</red>", Placeholder.unparsed("prefix", plugin.getLocaleManager().getRawMessage("prefix"))));
                        return;
                    }
                    builder.interval = interval;
                    builder.step = SetupStep.AWAITING_VIEW_DISTANCE;
                    plugin.getLocaleManager().sendMessage(player, "zone.setup.interval_set", Placeholder.unparsed("seconds", input));
                    plugin.getLocaleManager().sendMessage(player, "zone.setup.view_distance_prompt");
                } catch (NumberFormatException e) {
                    plugin.getLocaleManager().sendMessage(player, "zone.error.invalid_number");
                }
                break;
            case AWAITING_VIEW_DISTANCE:
                try {
                    int distance = Integer.parseInt(input);
                    if (distance < 0) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize("<prefix> <red>View distance cannot be negative.</red>", Placeholder.unparsed("prefix", plugin.getLocaleManager().getRawMessage("prefix"))));
                        return;
                    }
                    builder.viewDistance = distance;
                    plugin.getLocaleManager().sendMessage(player, "zone.setup.view_distance_set", Placeholder.unparsed("distance", input));
                    finishSetup(player, builder);
                } catch (NumberFormatException e) {
                    plugin.getLocaleManager().sendMessage(player, "zone.error.invalid_number");
                }
                break;
        }
    }

    private void finishSetup(Player player, ZoneBuilder builder) {
        RTPZone zone = builder.build();
        plugin.getRtpZoneManager().saveZone(zone);
        setupSessions.remove(player.getUniqueId());
        player.getInventory().remove(wand);
        plugin.getLocaleManager().sendMessage(player, "zone.setup.complete", Placeholder.unparsed("id", zone.getId()));
    }

    private String formatLocation(Location loc) {
        return String.format("%d, %d, %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private static class ZoneBuilder {
        final String id;
        final String worldName;
        SetupStep step = SetupStep.AWAITING_POS1;
        Location pos1;
        Location pos2;
        String target;
        int minRadius;
        int maxRadius;
        int interval;
        int viewDistance;

        ZoneBuilder(String id, String worldName) {
            this.id = id;
            this.worldName = worldName;
        }

        RTPZone build() {
            ConfigurationSection section = new YamlConfiguration();
            section.set("world", worldName);
            section.set("pos1", pos1);
            section.set("pos2", pos2);
            section.set("target", target);
            section.set("min-radius", minRadius);
            section.set("max-radius", maxRadius);
            section.set("interval", interval);

            Cuboid tempCuboid = new Cuboid(pos1, pos2);
            Location center = tempCuboid.getCenter();
            if (center != null) {
                double yOffset = JustRTP.getInstance().getHologramManager().getDefaultYOffset();
                Location holoLoc = center.clone().add(0, yOffset, 0);
                section.set("hologram.location", holoLoc);
                section.set("hologram.view-distance", viewDistance);
            }

            ConfigurationSection effectsSection = section.createSection("effects");
            ConfigurationSection onEnterSection = effectsSection.createSection("on_enter");
            onEnterSection.set("title.enabled", true);
            onEnterSection.set("title.main_title", "<green>Entered Zone</green>");
            onEnterSection.set("sound.enabled", true);
            onEnterSection.set("sound.name", "BLOCK_NOTE_BLOCK_PLING");

            ConfigurationSection onLeaveSection = effectsSection.createSection("on_leave");
            onLeaveSection.set("title.enabled", true);
            onLeaveSection.set("title.main_title", "<red>Left Zone</red>");

            ConfigurationSection waitingSection = effectsSection.createSection("waiting");
            waitingSection.set("title.enabled", true);
            waitingSection.set("title.fade_in", 0);
            waitingSection.set("title.stay", 25);
            waitingSection.set("title.fade_out", 5);
            waitingSection.set("title.main_title", "<gradient:red:gold>RTP ZONE</gradient>");
            waitingSection.set("title.subtitle", "<yellow>Teleporting in <time>s...");
            waitingSection.set("action_bar.enabled", true);
            waitingSection.set("action_bar.text", "<gray>Teleporting in <white><time>s</white>...");
            waitingSection.set("sound.enabled", true);
            waitingSection.set("sound.name", "BLOCK_NOTE_BLOCK_HAT");
            waitingSection.set("sound.volume", 0.5);
            waitingSection.set("sound.pitch", 1.2);

            ConfigurationSection teleportSection = effectsSection.createSection("teleport");
            teleportSection.set("title.enabled", true);
            teleportSection.set("title.main_title", "<dark_red>FIGHT!</dark_red>");
            teleportSection.set("sound.enabled", true);
            teleportSection.set("sound.name", "ENTITY_ENDER_DRAGON_GROWL");

            return new RTPZone(id, section);
        }
    }
}