package eu.kotori.justRTP.commands;

import eu.kotori.justRTP.JustRTP;
import eu.kotori.justRTP.utils.RTPZone;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RTPZoneCommand implements CommandExecutor {

    private final JustRTP plugin;

    public RTPZoneCommand(JustRTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLocaleManager().sendMessage(sender, "command.player_only");
            return true;
        }

        if (args.length == 0) {
            plugin.getLocaleManager().sendMessage(player, "zone.command.usage");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (!player.hasPermission("justrtp.admin.zone") && !subCommand.equals("ignore")) {
            plugin.getLocaleManager().sendMessage(player, "command.no_permission");
            return true;
        }

        switch (subCommand) {
            case "setup":
                if (args.length < 2) {
                    plugin.getLocaleManager().sendMessage(player, "zone.command.setup_usage");
                    return true;
                }
                plugin.getZoneSetupManager().startSetup(player, args[1]);
                break;
            case "delete":
                if (args.length < 2) {
                    plugin.getLocaleManager().sendMessage(player, "zone.command.delete_usage");
                    return true;
                }
                plugin.getRtpZoneManager().deleteZone(player, args[1]);
                break;
            case "list":
                plugin.getRtpZoneManager().listZones(player);
                break;
            case "cancel":
                plugin.getZoneSetupManager().cancelSetup(player);
                break;
            case "sethologram":
                handleSetHologram(player, args);
                break;
            case "delhologram":
                if (args.length < 2) {
                    plugin.getLocaleManager().sendMessage(player, "zone.command.delhologram_usage");
                    return true;
                }
                plugin.getRtpZoneManager().deleteHologramForZone(player, args[1]);
                break;
            case "ignore":
                if (!player.hasPermission("justrtp.command.zone.ignore")) {
                    plugin.getLocaleManager().sendMessage(player, "command.no_permission");
                    return true;
                }
                plugin.getRtpZoneManager().toggleIgnore(player);
                break;
            default:
                plugin.getLocaleManager().sendMessage(player, "zone.command.usage");
                break;
        }
        return true;
    }

    private void handleSetHologram(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getLocaleManager().sendMessage(player, "zone.command.sethologram_usage");
            return;
        }
        String zoneId = args[1];
        RTPZone zone = plugin.getRtpZoneManager().getZone(zoneId);
        if (zone == null) {
            plugin.getLocaleManager().sendMessage(player, "zone.error.not_found", Placeholder.unparsed("id", zoneId));
            return;
        }
        if (zone.getHologramLocation() != null) {
            plugin.getLocaleManager().sendMessage(player, "zone.command.hologram_already_exists", Placeholder.unparsed("id", zoneId));
            return;
        }

        double yOffset = plugin.getHologramManager().getDefaultYOffset();
        if (args.length >= 3) {
            try {
                yOffset = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                plugin.getLocaleManager().sendMessage(player, "zone.error.invalid_number");
                return;
            }
        }

        Location holoLoc = zone.getCenterLocation().clone().add(0, yOffset, 0);
        int viewDistance = zone.getHologramViewDistance();

        plugin.getRtpZoneManager().setHologramForZone(player, zoneId, holoLoc, viewDistance);
        plugin.getLocaleManager().sendMessage(player, "zone.command.sethologram_success", Placeholder.unparsed("id", zoneId));
    }
}