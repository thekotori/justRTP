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
            if (!serverAlreadyPresent) {
                options.addAll(plugin.getConfigManager().getProxyServers());
            }
        }

        StringUtil.copyPartialMatches(currentArg, options, completions);
        Collections.sort(completions);
        return completions;
    }
}