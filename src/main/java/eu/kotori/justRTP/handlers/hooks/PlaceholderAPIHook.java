package eu.kotori.justRTP.handlers.hooks;

import eu.kotori.justRTP.JustRTP;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlaceholderAPIHook extends PlaceholderExpansion {
    
    private final JustRTP plugin;
    
    public PlaceholderAPIHook(JustRTP plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public @NotNull String getIdentifier() {
        return "justrtp";
    }
    
    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }
    
    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true;
    }
    
    @Override
    public boolean canRegister() {
        return true;
    }
    
    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        
        try {
            switch (params.toLowerCase()) {
                case "cooldown":
                    return formatCooldown(plugin.getCooldownManager().getRemaining(player.getUniqueId()));
                    
                case "cooldown_raw":
                    return String.valueOf(plugin.getCooldownManager().getRemaining(player.getUniqueId()));
                    
                case "cooldown_total":
                    return String.valueOf(plugin.getConfigManager().getCooldown(player, player.getWorld()));
                    
                case "cost":
                    return String.format("%.2f", plugin.getConfigManager().getEconomyCost(player, player.getWorld()));
                    
                case "delay":
                    return String.valueOf(plugin.getConfigManager().getDelay(player, player.getWorld()));
                    
                case "world_min_radius":
                    return String.valueOf(getWorldMinRadius(player));
                    
                case "world_max_radius":
                    return String.valueOf(getWorldMaxRadius(player));
                    
                case "has_cooldown":
                    return String.valueOf(plugin.getCooldownManager().getRemaining(player.getUniqueId()) > 0);
                    
                case "is_delayed":
                    return String.valueOf(plugin.getDelayManager().isDelayed(player.getUniqueId()));
                    
                case "world_name":
                    return player.getWorld().getName();
                    
                case "permission_group":
                    return getPlayerPermissionGroup(player);
                    
                case "can_rtp":
                    boolean hasPermission = player.hasPermission("justrtp.command.rtp");
                    boolean noCooldown = plugin.getCooldownManager().getRemaining(player.getUniqueId()) <= 0;
                    boolean notDelayed = !plugin.getDelayManager().isDelayed(player.getUniqueId());
                    return String.valueOf(hasPermission && noCooldown && notDelayed);
                    
                case "cooldown_formatted":
                    long remaining = plugin.getCooldownManager().getRemaining(player.getUniqueId());
                    if (remaining <= 0) return "Ready";
                    return formatCooldownCompact(remaining);
                
                case "in_zone":
                    return String.valueOf(plugin.getRtpZoneManager().getPlayerZone(player) != null);
                
                case "current_zone":
                    String currentZone = plugin.getRtpZoneManager().getPlayerZone(player);
                    return currentZone != null ? currentZone : "None";
                
                case "zone_name":
                    String zoneName = plugin.getRtpZoneManager().getPlayerZone(player);
                    return zoneName != null ? zoneName : "None";
                    
                default:
                    if (params.toLowerCase().startsWith("zone_time_")) {
                        String zoneId = params.substring("zone_time_".length());
                        int countdown = plugin.getRtpZoneManager().getZoneCountdown(zoneId);
                        return countdown >= 0 ? String.valueOf(countdown) : "N/A";
                    }
                    return null;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing placeholder %" + getIdentifier() + "_" + params + "% for player " + player.getName() + ": " + e.getMessage());
            return "Error";
        }
    }
    
    private String formatCooldownCompact(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return minutes + "m" + (remainingSeconds > 0 ? remainingSeconds + "s" : "");
        } else {
            long hours = seconds / 3600;
            long remainingMinutes = (seconds % 3600) / 60;
            return hours + "h" + (remainingMinutes > 0 ? remainingMinutes + "m" : "");
        }
    }
    
    private String formatCooldown(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            if (remainingSeconds > 0) {
                return minutes + "m " + remainingSeconds + "s";
            } else {
                return minutes + "m";
            }
        } else {
            long hours = seconds / 3600;
            long remainingMinutes = (seconds % 3600) / 60;
            long remainingSeconds = seconds % 60;
            
            StringBuilder result = new StringBuilder();
            result.append(hours).append("h");
            
            if (remainingMinutes > 0) {
                result.append(" ").append(remainingMinutes).append("m");
            }
            if (remainingSeconds > 0) {
                result.append(" ").append(remainingSeconds).append("s");
            }
            
            return result.toString();
        }
    }
    
    private int getWorldMinRadius(Player player) {
        String worldName = player.getWorld().getName();
        return plugin.getConfig().getInt("custom_worlds." + worldName + ".min_radius", 
               plugin.getConfig().getInt("settings.min_radius", 100));
    }
    
    private int getWorldMaxRadius(Player player) {
        String worldName = player.getWorld().getName();
        return plugin.getConfig().getInt("custom_worlds." + worldName + ".max_radius", 
               plugin.getConfig().getInt("settings.max_radius", 5000));
    }
    
    private String getPlayerPermissionGroup(Player player) {
        if (!plugin.getConfig().getBoolean("permission_groups.enabled", false)) {
            return "default";
        }
        
        var groupsSection = plugin.getConfig().getConfigurationSection("permission_groups.groups");
        if (groupsSection == null) {
            return "default";
        }
        
        return groupsSection.getKeys(false).stream()
                .filter(groupName -> player.hasPermission("justrtp.group." + groupName))
                .min((g1, g2) -> Integer.compare(
                    groupsSection.getInt(g1 + ".priority", Integer.MAX_VALUE),
                    groupsSection.getInt(g2 + ".priority", Integer.MAX_VALUE)
                ))
                .orElse("default");
    }
}