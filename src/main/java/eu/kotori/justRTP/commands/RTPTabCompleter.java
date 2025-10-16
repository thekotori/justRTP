package eu.kotori.justRTP.commands;

import eu.kotori.justRTP.JustRTP;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class RTPTabCompleter implements TabCompleter {
    private final JustRTP plugin;
    public RTPTabCompleter(JustRTP plugin) { this.plugin = plugin; }
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        final List<String> completions = new ArrayList<>();
        final String currentArg = args[args.length - 1];
        final List<String> currentArgs = new ArrayList<>(Arrays.asList(args).subList(0, args.length - 1));

        Set<String> options = new HashSet<>();

        if (args.length == 1) {
            if (sender.hasPermission("justrtp.command.reload")) options.add("reload");
            if (sender.hasPermission("justrtp.admin")) options.add("proxystatus");
            if (sender.hasPermission("justrtp.command.confirm")) options.add("confirm");

            boolean creditsPermissionRequired = plugin.getConfig().getBoolean("settings.credits_command_requires_permission", true);
            if (!creditsPermissionRequired || sender.hasPermission("justrtp.command.credits")) {
                options.add("credits");
            }
        }

        if (sender.hasPermission("justrtp.command.rtp.world")) {
            boolean worldAlreadyPresent = Bukkit.getWorlds().stream().anyMatch(w -> currentArgs.contains(w.getName()));
            if (!worldAlreadyPresent) {
                List<String> validWorlds = Bukkit.getWorlds().stream()
                        .filter(plugin.getRtpService()::isRtpEnabled)
                        .map(World::getName)
                        .collect(Collectors.toList());
                options.addAll(validWorlds);
                plugin.getConfigManager().getWorldAliases().forEach((aliasKey, worldName) -> {
                    if (validWorlds.contains(worldName)) {
                        options.add(aliasKey);
                    }
                });
            }
        }

        if (sender.hasPermission("justrtp.command.rtp.others")) {
            boolean playerAlreadyPresent = Bukkit.getOnlinePlayers().stream().anyMatch(p -> currentArgs.contains(p.getName()));
            if (!playerAlreadyPresent) {
                options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
            }
        }

        if (plugin.getConfigManager().getProxyEnabled() && sender.hasPermission("justrtp.command.rtp.server")) {
            boolean serverAlreadyPresent = plugin.getConfigManager().getProxyServers().stream().anyMatch(s -> currentArgs.stream().anyMatch(ca -> ca.equalsIgnoreCase(s)));
            
            if (currentArg.contains(":")) {
                String[] parts = currentArg.split(":", 2);
                String serverPart = parts[0];
                String worldPart = parts.length > 1 ? parts[1] : "";
                
                Optional<String> matchingServer = plugin.getConfigManager().getProxyServers().stream()
                    .filter(s -> s.equalsIgnoreCase(serverPart))
                    .findFirst();
                
                if (matchingServer.isPresent()) {
                    if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
                        try {
                            List<String> worlds = plugin.getDatabaseManager().getServerWorlds(matchingServer.get()).get();
                            for (String world : worlds) {
                                if (world.toLowerCase().startsWith(worldPart.toLowerCase())) {
                                    completions.add(serverPart + ":" + world);
                                }
                            }
                            if (worlds.isEmpty()) {
                                for (String defaultWorld : new String[]{"world", "world_nether", "world_the_end"}) {
                                    if (defaultWorld.toLowerCase().startsWith(worldPart.toLowerCase())) {
                                        completions.add(serverPart + ":" + defaultWorld);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            plugin.debug("Failed to fetch worlds for tab completion: " + e.getMessage());
                            for (String defaultWorld : new String[]{"world", "world_nether", "world_the_end"}) {
                                if (defaultWorld.toLowerCase().startsWith(worldPart.toLowerCase())) {
                                    completions.add(serverPart + ":" + defaultWorld);
                                }
                            }
                        }
                    }
                    Collections.sort(completions);
                    return completions;
                }
            }
            
            if (!serverAlreadyPresent) {
                options.addAll(plugin.getConfigManager().getProxyServers());
                for (String server : plugin.getConfigManager().getProxyServers()) {
                    if ((server + ":").startsWith(currentArg.toLowerCase())) {
                        options.add(server + ":");
                    }
                }
            }
        }

        StringUtil.copyPartialMatches(currentArg, options, completions);
        Collections.sort(completions);
        return completions;
    }
}