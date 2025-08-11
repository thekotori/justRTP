package eu.kotori.justRTP.commands;

import eu.kotori.justRTP.JustRTP;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RTPCommand implements CommandExecutor {
    private final JustRTP plugin;

    public RTPCommand(JustRTP plugin) {
        this.plugin = plugin;
    }

    public record ParsedCommand(Player targetPlayer, World targetWorld, String targetServer, String proxyTargetWorld, Optional<Integer> minRadius, Optional<Integer> maxRadius, boolean isValid, String errorMessageKey, Map<String, String> errorPlaceholders) {
        public ParsedCommand(Player targetPlayer, World targetWorld, String targetServer, String proxyTargetWorld, Optional<Integer> minRadius, Optional<Integer> maxRadius, boolean isValid, String errorMessageKey) {
            this(targetPlayer, targetWorld, targetServer, proxyTargetWorld, minRadius, maxRadius, isValid, errorMessageKey, new HashMap<>());
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!plugin.getCommandManager().isAliasEnabled(label)) {
            return false;
        }

        if (sender instanceof Player player) {
            List<String> disabledWorlds = plugin.getConfig().getStringList("disabled_worlds");
            if (disabledWorlds.contains(player.getWorld().getName())) {
                plugin.getLocaleManager().sendMessage(sender, "command.command_disabled_in_world");
                return true;
            }
        }

        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "reload": handleReload(sender); return true;
                case "credits": handleCredits(sender); return true;
                case "proxystatus": handleProxyStatus(sender); return true;
                case "confirm": handleConfirm(sender); return true;
            }
        }

        processRtpRequest(sender, null, args, false);
        return true;
    }

    public CompletableFuture<Boolean> processRtpRequest(CommandSender sender, Player targetPlayer, String[] args, boolean crossServerNoDelay) {
        plugin.debug("Parsing RTP command arguments: " + String.join(" ", args));
        ParsedCommand parsed = parseArgs(sender, args, targetPlayer);
        if (!parsed.isValid()) {
            plugin.getLocaleManager().sendMessage(sender, parsed.errorMessageKey(), parsed.errorPlaceholders());
            return CompletableFuture.completedFuture(false);
        }

        plugin.debug("Parsed command: targetPlayer=" + parsed.targetPlayer().getName() + ", targetWorld=" + (parsed.targetWorld() != null ? parsed.targetWorld().getName() : "null") + ", targetServer=" + parsed.targetServer() + ", proxyTargetWorld=" + parsed.proxyTargetWorld());

        if (parsed.targetServer() != null && !parsed.targetServer().equalsIgnoreCase(plugin.getConfigManager().getProxyThisServerName())) {
            return validateAndInitiateProxyRtp(sender, parsed, args);
        } else {
            if (sender instanceof Player && parsed.targetPlayer() != null && sender.equals(parsed.targetPlayer())) {
                plugin.getLocaleManager().sendMessage(sender, "teleport.start_self");
            } else if (parsed.targetPlayer() != null) {
                plugin.getLocaleManager().sendMessage(sender, "teleport.start_other", Placeholder.unparsed("player", parsed.targetPlayer().getName()));
            }
            return validateAndInitiateLocalRtp(sender, parsed, crossServerNoDelay);
        }
    }

    public ParsedCommand parseArgs(CommandSender sender, String[] args, Player predefTarget) {
        List<String> remainingArgs = new ArrayList<>(Arrays.asList(args));
        Player targetPlayer = predefTarget;
        World targetWorld = null;
        String targetServer = null;
        String proxyTargetWorld = null;
        List<Integer> radii = new ArrayList<>();
        List<String> unparsedArgs = new ArrayList<>();

        List<String> availableServers = plugin.getConfigManager().getProxyEnabled() ? plugin.getConfigManager().getProxyServers() : Collections.emptyList();
        Iterator<String> it = remainingArgs.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            boolean consumed = false;

            Player p = Bukkit.getPlayer(arg);
            if (targetPlayer == null && p != null) {
                targetPlayer = p;
                consumed = true;
            }

            if (!consumed && targetServer == null && arg.contains(":")) {
                String[] parts = arg.split(":", 2);
                if (availableServers.stream().anyMatch(s -> s.equalsIgnoreCase(parts[0]))) {
                    targetServer = parts[0];
                    proxyTargetWorld = parts[1];
                    consumed = true;
                }
            }

            if (!consumed && targetServer == null && availableServers.stream().anyMatch(s -> s.equalsIgnoreCase(arg))) {
                targetServer = arg;
                consumed = true;
            }

            if (!consumed && targetWorld == null) {
                String resolvedWorldName = plugin.getConfigManager().resolveWorldAlias(arg);
                World w = Bukkit.getWorld(resolvedWorldName);
                if (w != null) {
                    targetWorld = w;
                    consumed = true;
                }
            }

            if (!consumed) {
                unparsedArgs.add(arg);
            }
        }

        it = unparsedArgs.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            try {
                radii.add(Integer.parseInt(arg));
                it.remove();
            } catch (NumberFormatException ignored) {}
        }

        if (targetServer != null && proxyTargetWorld == null && unparsedArgs.size() == 1) {
            proxyTargetWorld = unparsedArgs.remove(0);
        }

        if (!unparsedArgs.isEmpty()) {
            return new ParsedCommand(null, null, null, null, Optional.empty(), Optional.empty(), false, "command.usage");
        }

        if (targetPlayer == null) {
            if (sender instanceof Player p) {
                targetPlayer = p;
            } else {
                return new ParsedCommand(null, null, null, null, Optional.empty(), Optional.empty(), false, "command.player_only");
            }
        }

        if (targetWorld == null && targetServer == null) {
            targetWorld = targetPlayer.getWorld();
        }

        if (targetWorld != null && targetServer != null) {
            return new ParsedCommand(null, null, null, null, Optional.empty(), Optional.empty(), false, "command.usage");
        }

        Optional<Integer> minRadius = Optional.empty();
        Optional<Integer> maxRadius = Optional.empty();
        if (!radii.isEmpty()) {
            if (!sender.hasPermission("justrtp.command.rtp.radius")) {
                return new ParsedCommand(null, null, null, null, Optional.empty(), Optional.empty(), false, "command.no_permission");
            }
            radii.sort(Comparator.naturalOrder());
            if (radii.size() == 1) {
                maxRadius = Optional.of(radii.get(0));
            } else {
                minRadius = Optional.of(radii.get(0));
                maxRadius = Optional.of(radii.get(1));
            }
        }

        return new ParsedCommand(targetPlayer, targetWorld, targetServer, proxyTargetWorld, minRadius, maxRadius, true, "");
    }

    private CompletableFuture<Boolean> validateAndInitiateProxyRtp(CommandSender sender, ParsedCommand parsed, String[] rawArgs) {
        plugin.debug("Validating and initiating proxy RTP.");
        Player target = parsed.targetPlayer();

        if (!plugin.getProxyManager().isProxyEnabled()) {
            plugin.getLocaleManager().sendMessage(sender, "proxy.disabled");
            return CompletableFuture.completedFuture(false);
        }
        if (!sender.hasPermission("justrtp.command.rtp.server")) {
            plugin.getLocaleManager().sendMessage(sender, "command.no_permission");
            return CompletableFuture.completedFuture(false);
        }

        long remainingCooldown = plugin.getCooldownManager().getRemaining(target.getUniqueId());
        if (remainingCooldown > 0) {
            plugin.getLocaleManager().sendMessage(target, "teleport.cooldown", Placeholder.unparsed("time", String.valueOf(remainingCooldown)));
            return CompletableFuture.completedFuture(false);
        }

        int cooldown = plugin.getConfigManager().getCooldown(target, target.getWorld());
        plugin.getCooldownManager().setCooldown(target.getUniqueId(), cooldown);

        plugin.getCrossServerManager().sendFindLocationRequest(target, parsed.targetServer(), parsed.proxyTargetWorld(), parsed.minRadius(), parsed.maxRadius(), rawArgs);
        return CompletableFuture.completedFuture(true);
    }

    private CompletableFuture<Boolean> validateAndInitiateLocalRtp(CommandSender sender, ParsedCommand parsed, boolean crossServerNoDelay) {
        plugin.debug("Validating and initiating local RTP.");
        Player targetPlayer = parsed.targetPlayer();
        World targetWorld = parsed.targetWorld();

        if (targetWorld == null) {
            plugin.getLocaleManager().sendMessage(sender, "command.world_not_found", Placeholder.unparsed("world", "null"));
            return CompletableFuture.completedFuture(false);
        }

        if (!plugin.getRtpService().isRtpEnabled(targetWorld)) {
            plugin.getLocaleManager().sendMessage(sender, "command.world_disabled", Placeholder.unparsed("world", targetWorld.getName()));
            return CompletableFuture.completedFuture(false);
        }

        if (!(sender instanceof ConsoleCommandSender) && !crossServerNoDelay) {
            long remainingCooldown = plugin.getCooldownManager().getRemaining(targetPlayer.getUniqueId());
            if (remainingCooldown > 0) {
                plugin.getLocaleManager().sendMessage(sender, "teleport.cooldown", Placeholder.unparsed("time", String.valueOf(remainingCooldown)));
                return CompletableFuture.completedFuture(false);
            }
        }

        double cost = plugin.getConfigManager().getEconomyCost(targetPlayer, targetWorld);
        final double finalCost = Math.max(0, cost);
        boolean requireConfirmation = plugin.getConfig().getBoolean("economy.require_confirmation", true);

        if (plugin.getConfig().getBoolean("economy.enabled") && finalCost > 0 && plugin.getVaultHook().hasEconomy()) {
            if (plugin.getVaultHook().getBalance(targetPlayer) < finalCost) {
                plugin.getLocaleManager().sendMessage(targetPlayer, "economy.not_enough_money", Placeholder.unparsed("cost", String.valueOf(finalCost)));
                return CompletableFuture.completedFuture(false);
            }
            if (requireConfirmation && sender instanceof Player && sender.equals(targetPlayer) && !plugin.getConfirmationManager().hasPendingConfirmation(targetPlayer)) {
                CompletableFuture<Boolean> confirmationFuture = new CompletableFuture<>();
                plugin.getConfirmationManager().addPendingConfirmation(targetPlayer, () ->
                        executeTeleportationLogic(sender, parsed, crossServerNoDelay, finalCost, true)
                                .thenAccept(confirmationFuture::complete));
                plugin.getLocaleManager().sendMessage(targetPlayer, "economy.needs_confirmation", Placeholder.unparsed("cost", String.valueOf(finalCost)));
                return confirmationFuture;
            }
        }
        return executeTeleportationLogic(sender, parsed, crossServerNoDelay, finalCost, false);
    }

    public CompletableFuture<Boolean> executeTeleportationLogic(CommandSender sender, ParsedCommand parsed, boolean crossServerNoDelay) {
        return executeTeleportationLogic(sender, parsed, crossServerNoDelay, 0.0, false);
    }

    private CompletableFuture<Boolean> executeTeleportationLogic(CommandSender sender, ParsedCommand parsed, boolean crossServerNoDelay, double cost, boolean wasConfirmed) {
        Player targetPlayer = parsed.targetPlayer();
        World targetWorld = parsed.targetWorld();
        plugin.debug("Executing teleport logic for " + targetPlayer.getName() + " to world " + targetWorld.getName());

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        int delay = (sender instanceof Player && targetPlayer.equals(sender) && !crossServerNoDelay) ? plugin.getConfigManager().getDelay(targetPlayer, targetWorld) : 0;
        int cooldown = plugin.getConfigManager().getCooldown(targetPlayer, targetWorld);

        plugin.getDelayManager().startDelay(targetPlayer, () -> {
            if (plugin.getConfig().getBoolean("economy.enabled") && cost > 0 && plugin.getVaultHook().hasEconomy()) {
                if (!plugin.getVaultHook().withdrawPlayer(targetPlayer, cost)) {
                    plugin.getLocaleManager().sendMessage(targetPlayer, "economy.not_enough_money", Placeholder.unparsed("cost", String.valueOf(cost)));
                    future.complete(false);
                    return;
                }
                if(wasConfirmed || !plugin.getConfig().getBoolean("economy.require_confirmation", true)) {
                    plugin.getLocaleManager().sendMessage(targetPlayer, "economy.payment_success", Placeholder.unparsed("cost", String.valueOf(cost)));
                }
            }

            if (!(sender instanceof ConsoleCommandSender) && !crossServerNoDelay) {
                plugin.getCooldownManager().setCooldown(targetPlayer.getUniqueId(), cooldown);
            }

            plugin.getTeleportQueueManager().requestTeleport(targetPlayer, targetWorld, parsed.minRadius(), parsed.maxRadius())
                    .thenAccept(future::complete);
        }, delay);
        return future;
    }

    private void handleConfirm(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getLocaleManager().sendMessage(sender, "command.player_only");
            return;
        }
        if (!player.hasPermission("justrtp.command.confirm")) {
            plugin.getLocaleManager().sendMessage(sender, "command.no_permission");
            return;
        }
        plugin.getConfirmationManager().confirm(player);
    }

    private void handleProxyStatus(CommandSender sender) {
        if (!sender.hasPermission("justrtp.admin")) {
            plugin.getLocaleManager().sendMessage(sender, "command.no_permission");
            return;
        }

        MiniMessage mm = MiniMessage.miniMessage();
        String thisServerName = plugin.getConfigManager().getProxyThisServerName();
        boolean isProxyEnabled = plugin.getProxyManager().isProxyEnabled();

        sender.sendMessage(mm.deserialize("<br><gradient:#20B2AA:#7FFFD4><b>JustRTP Proxy Status</b></gradient>"));
        sender.sendMessage(mm.deserialize("<gray>--------------------------------------------------</gray>"));

        sender.sendMessage(isProxyEnabled ?
                mm.deserialize("<green>✔</green> Proxy feature is <green><b>enabled</b></green> in config.yml.") :
                mm.deserialize("<red>✖</red> Proxy feature is <red><b>disabled</b></red> in config.yml."));

        if (!isProxyEnabled) return;

        sender.sendMessage(thisServerName.isEmpty() || thisServerName.equals("server-name") ?
                mm.deserialize("<red>✖</red> <white>'this_server_name' is <red><b>not set</b></red>! This is required.") :
                mm.deserialize("<green>✔</green> <white>This server is identified as: <gold>" + thisServerName + "</gold>"));

        boolean mysqlEnabled = plugin.getConfigManager().isProxyMySqlEnabled();
        sender.sendMessage(mysqlEnabled ?
                mm.deserialize("<green>✔</green> MySQL is <green><b>enabled</b></green> in mysql.yml.") :
                mm.deserialize("<red>✖</red> MySQL is <red><b>disabled</b></red> in mysql.yml."));

        if (mysqlEnabled && plugin.getDatabaseManager() != null) {
            sender.sendMessage(plugin.getDatabaseManager().isConnected() ?
                    mm.deserialize("<green>✔</green> MySQL connection is <green><b>active</b></green>.") :
                    mm.deserialize("<red>✖</red> MySQL connection is <red><b>inactive</b></red>. Check credentials/firewall."));
        }

        sender.sendMessage(mm.deserialize("<gray>--------------------------------------------------</gray>"));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("justrtp.command.reload")) { plugin.getLocaleManager().sendMessage(sender, "command.no_permission"); return; }
        plugin.reload();
        plugin.getLocaleManager().sendMessage(sender, "command.reload");
    }

    private void handleCredits(CommandSender sender) {
        boolean permissionRequired = plugin.getConfig().getBoolean("settings.credits_command_requires_permission", true);
        if (permissionRequired && !sender.hasPermission("justrtp.command.credits")) {
            plugin.getLocaleManager().sendMessage(sender, "command.no_permission");
            return;
        }
        sendCredits(sender);
    }

    private void sendCredits(CommandSender sender) {
        String version = plugin.getDescription().getVersion();
        MiniMessage mm = MiniMessage.miniMessage();
        String link = "https://builtbybit.com/resources/justrtp-lightweight-fast-randomtp.70322/";
        List<String> creditsMessage = Arrays.asList("", "<gradient:#20B2AA:#7FFFD4>JustRTP</gradient> <gray>v" + version, "", "<gray>Developed by <white>kotori</white>.", "<click:open_url:'" + link + "'><hover:show_text:'<green>Click to visit!'><#7FFFD4><u>Click here to check for updates!</u></hover></click>", "");
        sender.sendMessage(mm.deserialize("<gradient:#20B2AA:#7FFFD4>--------------------------------------------------<gradient>"));
        creditsMessage.forEach(line -> sender.sendMessage(mm.deserialize(" " + line)));
        sender.sendMessage(mm.deserialize("<gradient:#7FFFD4:#20B2AA>--------------------------------------------------<gradient>"));
    }
}