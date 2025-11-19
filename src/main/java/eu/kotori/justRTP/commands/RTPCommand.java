package eu.kotori.justRTP.commands;

import eu.kotori.justRTP.JustRTP;
import eu.kotori.justRTP.events.PlayerRTPEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import eu.kotori.justRTP.utils.TimeUtils;
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
                case "help": handleHelp(sender); return true;
                case "location": handleLocation(sender, args); return true;
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

        if (parsed.targetServer() != null && parsed.targetServer().equalsIgnoreCase(plugin.getConfigManager().getProxyThisServerName())) {
            String worldName = parsed.proxyTargetWorld() != null ? parsed.proxyTargetWorld() : "world";
            plugin.getLocaleManager().sendMessage(sender, "proxy.same_server_error", 
                Placeholder.unparsed("this_server", parsed.targetServer()),
                Placeholder.unparsed("world", worldName));
            return CompletableFuture.completedFuture(false);
        }
        
        if (parsed.targetServer() != null && !parsed.targetServer().equalsIgnoreCase(plugin.getConfigManager().getProxyThisServerName())) {
            return validateAndInitiateProxyRtp(sender, parsed, args);
        } else {
            if (sender instanceof Player && parsed.targetPlayer() != null && !sender.equals(parsed.targetPlayer()) && !sender.hasPermission("justrtp.command.rtp.others")) {
                plugin.getLocaleManager().sendMessage(sender, "command.no_permission");
                return CompletableFuture.completedFuture(false);
            }
            
            if (sender instanceof Player && plugin.getDelayManager().isDelayed(parsed.targetPlayer().getUniqueId())) {
                plugin.getLocaleManager().sendMessage(sender, "teleport.already_in_progress");
                return CompletableFuture.completedFuture(false);
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
                    proxyTargetWorld = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
                    consumed = true;
                }
            }

            if (!consumed && targetServer == null && availableServers.stream().anyMatch(s -> s.equalsIgnoreCase(arg))) {
                targetServer = arg;
                proxyTargetWorld = "world";
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
            String configuredDefaultWorld = plugin.getConfig().getString("settings.default_world", "").trim();

            if (!configuredDefaultWorld.isEmpty()) {
                if (sender instanceof Player p && p.hasPermission("justrtp.bypass.default_world")) {
                    targetWorld = targetPlayer.getWorld();
                } else {
                    World defaultWorldObj = Bukkit.getWorld(configuredDefaultWorld);
                    if (defaultWorldObj != null) {
                        targetWorld = defaultWorldObj;
                    } else {
                        plugin.debug("Configured default RTP world not found: " + configuredDefaultWorld + ", falling back to player world");
                        targetWorld = targetPlayer.getWorld();
                    }
                }
            } else {
                targetWorld = targetPlayer.getWorld();
            }
        }

        if (targetWorld != null && plugin.getConfigManager().isSpawnRedirectEnabled()) {
            String spawnWorldName = plugin.getConfigManager().getSpawnWorldName();
            String redirectTargetWorldName = plugin.getConfigManager().getSpawnRedirectTargetWorld();
            
            if (targetWorld.getName().equalsIgnoreCase(spawnWorldName) && args.length == 0) {
                World redirectWorld = Bukkit.getWorld(redirectTargetWorldName);
                if (redirectWorld != null) {
                    targetWorld = redirectWorld;
                    plugin.debug("Spawn redirect: " + spawnWorldName + " -> " + redirectTargetWorldName);
                    
                    if (plugin.getConfigManager().shouldNotifySpawnRedirect() && sender instanceof Player) {
                        plugin.getLocaleManager().sendMessage(sender, "spawn_redirect.redirected", 
                            Placeholder.unparsed("from_world", spawnWorldName),
                            Placeholder.unparsed("to_world", redirectTargetWorldName));
                    }
                } else {
                    plugin.debug("Spawn redirect target world not found: " + redirectTargetWorldName);
                }
            }
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
        if (sender instanceof Player && !sender.equals(target) && !sender.hasPermission("justrtp.command.rtp.others")) {
            plugin.getLocaleManager().sendMessage(sender, "command.no_permission");
            return CompletableFuture.completedFuture(false);
        }
        
        if (plugin.getDelayManager().isDelayed(target.getUniqueId())) {
            plugin.getLocaleManager().sendMessage(sender, "teleport.already_in_progress");
            return CompletableFuture.completedFuture(false);
        }
        
        if (plugin.getTeleportQueueManager().isPlayerInProgress(target.getUniqueId())) {
            plugin.debug("Player " + target.getName() + " already has a local teleport in queue");
            plugin.getLocaleManager().sendMessage(sender, "teleport.already_in_progress");
            return CompletableFuture.completedFuture(false);
        }

        String worldName = target.getWorld().getName();
        if (!target.isOp() && !target.hasPermission("justrtp.cooldown.bypass")) {
            long remainingCooldown = plugin.getCooldownManager().getRemaining(target.getUniqueId(), worldName);
            if (remainingCooldown > 0) {
                plugin.getLocaleManager().sendMessage(target, "teleport.cooldown", Placeholder.unparsed("time", TimeUtils.formatDuration(remainingCooldown)));
                return CompletableFuture.completedFuture(false);
            }
        }

        if (!target.isOp() && !target.hasPermission("justrtp.cooldown.bypass")) {
            int cooldown = plugin.getConfigManager().getCooldown(target, target.getWorld());
            plugin.getCooldownManager().setCooldown(target.getUniqueId(), worldName, cooldown);
            plugin.debug("Set cooldown for " + target.getName() + " in world " + worldName + ": " + cooldown + " seconds");
        } else {
            plugin.debug("Skipped proxy cooldown for " + target.getName() + " (OP or bypass permission)");
        }

        String serverAlias = plugin.getConfigManager().getProxyServerAlias(parsed.targetServer());
        plugin.getLocaleManager().sendMessage(sender, "proxy.searching", Placeholder.unparsed("server", serverAlias));

        plugin.getCrossServerManager().sendFindLocationRequest(target, parsed.targetServer(), parsed.proxyTargetWorld(), parsed.minRadius(), parsed.maxRadius(), rawArgs);
        return CompletableFuture.completedFuture(true);
    }

    private CompletableFuture<Boolean> validateAndInitiateLocalRtp(CommandSender sender, ParsedCommand parsed, boolean crossServerNoDelay) {
        plugin.debug("Validating and initiating local RTP for " + parsed.targetPlayer().getName() + " (crossServerNoDelay=" + crossServerNoDelay + ")");
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
        
        if (plugin.getDelayManager().isDelayed(targetPlayer.getUniqueId())) {
            plugin.debug("Player " + targetPlayer.getName() + " is already delayed (in progress)");
            plugin.getLocaleManager().sendMessage(sender, "teleport.already_in_progress");
            return CompletableFuture.completedFuture(false);
        }
        
        if (plugin.getTeleportQueueManager().isPlayerInProgress(targetPlayer.getUniqueId())) {
            plugin.debug("Player " + targetPlayer.getName() + " is already in teleport queue");
            plugin.getLocaleManager().sendMessage(sender, "teleport.already_in_progress");
            return CompletableFuture.completedFuture(false);
        }

        if (!(sender instanceof ConsoleCommandSender) && !crossServerNoDelay) {
            if (!targetPlayer.isOp() && !targetPlayer.hasPermission("justrtp.cooldown.bypass")) {
                String worldName = targetWorld.getName();
                long remainingCooldown = plugin.getCooldownManager().getRemaining(targetPlayer.getUniqueId(), worldName);
                if (remainingCooldown > 0) {
                    plugin.getLocaleManager().sendMessage(sender, "teleport.cooldown", Placeholder.unparsed("time", TimeUtils.formatDuration(remainingCooldown)));
                    return CompletableFuture.completedFuture(false);
                }
            }
        }

        double cost = plugin.getConfigManager().getEconomyCost(targetPlayer, targetWorld);
        
        if (parsed.maxRadius().isPresent()) {
            double radiusCost = plugin.getConfigManager().getRadiusBasedCost(targetWorld, parsed.maxRadius().get());
            cost += radiusCost;
        }
        
        final double finalCost = Math.max(0, cost);
        
        PlayerRTPEvent rtpEvent = new PlayerRTPEvent(
            targetPlayer, 
            targetWorld, 
            parsed.minRadius().orElse(null), 
            parsed.maxRadius().orElse(null),
            finalCost,
            false, 
            null
        );
        Bukkit.getPluginManager().callEvent(rtpEvent);
        
        if (rtpEvent.isCancelled()) {
            plugin.debug("PlayerRTPEvent was cancelled by another plugin for " + targetPlayer.getName());
            return CompletableFuture.completedFuture(false);
        }
        
        World eventTargetWorld = rtpEvent.getTargetWorld();
        if (!eventTargetWorld.equals(targetWorld)) {
            plugin.debug("Target world changed by PlayerRTPEvent: " + targetWorld.getName() + " -> " + eventTargetWorld.getName());
            targetWorld = eventTargetWorld;
        }
        
        boolean requireConfirmation = plugin.getConfig().getBoolean("economy.require_confirmation", true);

        if (plugin.getConfig().getBoolean("economy.enabled") && finalCost > 0 && plugin.getVaultHook().hasEconomy()) {
            if (plugin.getVaultHook().getBalance(targetPlayer) < finalCost) {
                plugin.getLocaleManager().sendMessage(targetPlayer, "economy.not_enough_money", Placeholder.unparsed("cost", String.valueOf(finalCost)));
                return CompletableFuture.completedFuture(false);
            }
            if (requireConfirmation && sender instanceof Player && sender.equals(targetPlayer) && !plugin.getConfirmationManager().hasPendingConfirmation(targetPlayer)) {
                CompletableFuture<Boolean> confirmationFuture = new CompletableFuture<>();
                plugin.getConfirmationManager().addPendingConfirmation(targetPlayer, () -> {
                    if (sender instanceof Player && sender.equals(targetPlayer)) {
                        plugin.getLocaleManager().sendMessage(sender, "teleport.start_self");
                    } else if (parsed.targetPlayer() != null) {
                        plugin.getLocaleManager().sendMessage(sender, "teleport.start_other", Placeholder.unparsed("player", parsed.targetPlayer().getName()));
                    }
                    executeTeleportationLogic(sender, parsed, crossServerNoDelay, finalCost, true)
                            .thenAccept(confirmationFuture::complete);
                });
                plugin.getLocaleManager().sendMessage(targetPlayer, "economy.needs_confirmation", Placeholder.unparsed("cost", String.valueOf(finalCost)));
                return confirmationFuture;
            }
        }
        
        if (sender instanceof Player && sender.equals(targetPlayer)) {
            plugin.getLocaleManager().sendMessage(sender, "teleport.start_self");
        } else if (parsed.targetPlayer() != null) {
            plugin.getLocaleManager().sendMessage(sender, "teleport.start_other", Placeholder.unparsed("player", parsed.targetPlayer().getName()));
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
                if (!targetPlayer.isOp() && !targetPlayer.hasPermission("justrtp.cooldown.bypass")) {
                    String worldName = targetWorld.getName();
                    plugin.getCooldownManager().setCooldown(targetPlayer.getUniqueId(), worldName, cooldown);
                    plugin.debug("Set per-world cooldown for " + targetPlayer.getName() + " in " + worldName + ": " + cooldown + "s");
                } else {
                    plugin.debug("Skipped cooldown for " + targetPlayer.getName() + " (OP or bypass permission)");
                }
            }

            plugin.getTeleportQueueManager().requestTeleport(targetPlayer, targetWorld, parsed.minRadius(), parsed.maxRadius(), cost)
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

        sender.sendMessage(mm.deserialize("<br><gradient:#20B2AA:#7FFFD4><b>JustRTP Proxy & Network Status</b></gradient>"));
        sender.sendMessage(mm.deserialize("<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>"));
        sender.sendMessage(mm.deserialize(""));

        sender.sendMessage(mm.deserialize("<gradient:#20B2AA:#7FFFD4>▶ Proxy Configuration</gradient>"));
        sender.sendMessage(isProxyEnabled ?
                mm.deserialize("  <green>✔</green> Proxy feature: <green><b>ENABLED</b></green>") :
                mm.deserialize("  <red>✖</red> Proxy feature: <red><b>DISABLED</b></red>"));

        if (!isProxyEnabled) {
            sender.sendMessage(mm.deserialize("  <gray>└─ Enable in <white>config.yml<gray> -> <white>proxy.enabled: true"));
            sender.sendMessage(mm.deserialize(""));
            sender.sendMessage(mm.deserialize("<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>"));
            return;
        }

        sender.sendMessage(thisServerName.isEmpty() || thisServerName.equals("server-name") ?
                mm.deserialize("  <red>✖</red> Server name: <red><b>NOT SET</b></red> <gray>(Required!)") :
                mm.deserialize("  <green>✔</green> Server name: <gold>" + thisServerName + "</gold>"));
        
        sender.sendMessage(mm.deserialize("  <gray>└─ Tip: Use <white>/rtp " + thisServerName + ":world<gray> for local RTP"));
        sender.sendMessage(mm.deserialize(""));

        sender.sendMessage(mm.deserialize("<gradient:#20B2AA:#7FFFD4>▶ MySQL Database</gradient>"));
        boolean mysqlEnabled = plugin.getConfigManager().isProxyMySqlEnabled();
        sender.sendMessage(mysqlEnabled ?
                mm.deserialize("  <green>✔</green> MySQL: <green><b>ENABLED</b></green>") :
                mm.deserialize("  <red>✖</red> MySQL: <red><b>DISABLED</b></red>"));

        if (mysqlEnabled && plugin.getDatabaseManager() != null) {
            boolean mysqlConnected = plugin.getDatabaseManager().isConnected();
            sender.sendMessage(mysqlConnected ?
                    mm.deserialize("  <green>✔</green> Connection: <green><b>ACTIVE</b></green>") :
                    mm.deserialize("  <red>✖</red> Connection: <red><b>FAILED</b></red>"));
            
            if (mysqlConnected) {
                Map<String, String> dbInfo = plugin.getDatabaseManager().getConnectionInfo();
                sender.sendMessage(mm.deserialize("  <gray>├─ Host: <white>" + dbInfo.getOrDefault("host", "N/A") + ":" + dbInfo.getOrDefault("port", "N/A")));
                sender.sendMessage(mm.deserialize("  <gray>├─ Database: <white>" + dbInfo.getOrDefault("database", "N/A")));
                sender.sendMessage(mm.deserialize("  <gray>└─ Pool: <white>" + dbInfo.getOrDefault("pool_size", "0") + "<gray> active connections"));
            } else {
                sender.sendMessage(mm.deserialize("  <gray>└─ Check credentials/firewall in <white>mysql.yml"));
            }
        } else {
            sender.sendMessage(mm.deserialize("  <gray>└─ Enable in <white>mysql.yml<gray> for cross-server support"));
        }
        sender.sendMessage(mm.deserialize(""));

        sender.sendMessage(mm.deserialize("<gradient:#20B2AA:#7FFFD4>▶ Redis Cache</gradient> <gray>(Optional)"));
        boolean redisEnabled = plugin.getConfigManager().isRedisEnabled();
        sender.sendMessage(redisEnabled ?
                mm.deserialize("  <green>✔</green> Redis: <green><b>ENABLED</b></green> <gray>(Check redis.yml for details)") :
                mm.deserialize("  <gray>-</gray> Redis: <gray><b>DISABLED</b></gray> <gray>(Using memory/MySQL fallback)"));
        
        if (redisEnabled) {
            String redisHost = plugin.getConfig().getString("redis.connection.host", "localhost");
            int redisPort = plugin.getConfig().getInt("redis.connection.port", 6379);
            sender.sendMessage(mm.deserialize("  <gray>├─ Host: <white>" + redisHost + ":" + redisPort));
            
            List<String> storageTypes = new ArrayList<>();
            if (plugin.getConfig().getBoolean("redis.storage.cooldowns")) storageTypes.add("cooldowns");
            if (plugin.getConfig().getBoolean("redis.storage.delays")) storageTypes.add("delays");
            if (plugin.getConfig().getBoolean("redis.storage.cache")) storageTypes.add("cache");
            if (plugin.getConfig().getBoolean("redis.storage.teleport_requests")) storageTypes.add("requests");
            
            if (!storageTypes.isEmpty()) {
                sender.sendMessage(mm.deserialize("  <gray>├─ Storage: <aqua>" + String.join("<gray>, <aqua>", storageTypes)));
            }
            
            boolean pubsubEnabled = plugin.getConfig().getBoolean("redis.pubsub.enabled", false);
            sender.sendMessage(mm.deserialize("  <gray>└─ PubSub: " + (pubsubEnabled ? "<green>enabled" : "<gray>disabled")));
        } else {
            sender.sendMessage(mm.deserialize("  <gray>└─ Plugin works fully without Redis"));
        }
        
        sender.sendMessage(mm.deserialize(""));
        sender.sendMessage(mm.deserialize("<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>"));
        sender.sendMessage(mm.deserialize("<gradient:#20B2AA:#7FFFD4>💡 Tip:</gradient> <gray>Use <white>/rtp <gold>server<white>:<aqua>world<gray> for cross-server RTP"));
        sender.sendMessage(mm.deserialize("<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>"));
    }

    private void handleHelp(CommandSender sender) {
        MiniMessage mm = MiniMessage.miniMessage();
        String thisServer = plugin.getConfigManager().getProxyThisServerName();
        boolean proxyEnabled = plugin.getConfigManager().getProxyEnabled();
        
        sender.sendMessage(mm.deserialize(""));
        sender.sendMessage(mm.deserialize("<gradient:#20B2AA:#7FFFD4>━━━━━━━━━━━━ JustRTP Command Guide ━━━━━━━━━━━━</gradient>"));
        sender.sendMessage(mm.deserialize(""));
        sender.sendMessage(mm.deserialize("<yellow><b>Basic Usage:</b>"));
        sender.sendMessage(mm.deserialize("  <white>/rtp                    <dark_gray>→ <gray>Random location (current world)"));
        sender.sendMessage(mm.deserialize("  <white>/rtp <aqua>world_nether        <dark_gray>→ <gray>Random location in world_nether"));
        sender.sendMessage(mm.deserialize("  <white>/rtp <aqua>world_the_end       <dark_gray>→ <gray>Random location in the end"));
        sender.sendMessage(mm.deserialize(""));
        
        if (proxyEnabled) {
            sender.sendMessage(mm.deserialize("<gold><b>Cross-Server Usage:</b>"));
            sender.sendMessage(mm.deserialize("  <white>/rtp <gold>lobby2              <dark_gray>→ <gray>Default world on lobby2"));
            sender.sendMessage(mm.deserialize("  <white>/rtp <gold>lobby2<white>:<aqua>world_nether <dark_gray>→ <gray>world_nether on lobby2"));
            sender.sendMessage(mm.deserialize("  <white>/rtp <gold>factions<white>:<aqua>world       <dark_gray>→ <gray>world on factions server"));
            sender.sendMessage(mm.deserialize(""));
            sender.sendMessage(mm.deserialize("<gray>💡 <white>Important: <gray>For <yellow>same server<gray>, use <white>/rtp <aqua>world"));
            sender.sendMessage(mm.deserialize("<gray>   Current server: <gold>" + thisServer));
            sender.sendMessage(mm.deserialize(""));
        }
        
        sender.sendMessage(mm.deserialize("<aqua><b>Advanced Usage:</b>"));
        sender.sendMessage(mm.deserialize("  <white>/rtp <player>           <dark_gray>→ <gray>Teleport another player"));
        sender.sendMessage(mm.deserialize("  <white>/rtp <radius>           <dark_gray>→ <gray>Custom max radius"));
        sender.sendMessage(mm.deserialize("  <white>/rtp <min> <max>        <dark_gray>→ <gray>Custom min/max radius"));
        sender.sendMessage(mm.deserialize("  <white>/rtp location <gold><name>  <dark_gray>→ <gray>Teleport to custom location"));
        sender.sendMessage(mm.deserialize(""));
        sender.sendMessage(mm.deserialize("<green><b>Special Commands:</b>"));
        sender.sendMessage(mm.deserialize("  <white>/rtp confirm            <dark_gray>→ <gray>Confirm paid teleport"));
        sender.sendMessage(mm.deserialize("  <white>/rtp proxystatus        <dark_gray>→ <gray>Check proxy/database status"));
        sender.sendMessage(mm.deserialize("  <white>/rtp reload             <dark_gray>→ <gray>Reload configuration <gray>(admin)"));
        sender.sendMessage(mm.deserialize(""));
        sender.sendMessage(mm.deserialize("<gradient:#20B2AA:#7FFFD4>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
    }

    private void handleLocation(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLocaleManager().sendMessage(sender, "command.player_only");
            return;
        }

        if (!sender.hasPermission("justrtp.command.rtp.location")) {
            plugin.getLocaleManager().sendMessage(sender, "command.no_permission");
            return;
        }

        if (args.length < 2) {
            plugin.getLocaleManager().sendMessage(sender, "custom_locations.usage");
            return;
        }

        String locationName = args[1].toLowerCase();
        plugin.getCustomLocationManager().teleportToLocation(player, locationName);
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
        String version = plugin.getPluginMeta().getVersion();
        MiniMessage mm = MiniMessage.miniMessage();
        String link = "https://builtbybit.com/resources/justrtp-lightweight-fast-randomtp.70322/";
        List<String> creditsMessage = Arrays.asList("", "<gradient:#20B2AA:#7FFFD4>JustRTP</gradient> <gray>v" + version, "", "<gray>Developed by <white>kotori</white>.", "<click:open_url:'" + link + "'><hover:show_text:'<green>Click to visit!'><#7FFFD4><u>Click here to check for updates!</u></hover></click>", "");
        sender.sendMessage(mm.deserialize("<gradient:#20B2AA:#7FFFD4>--------------------------------------------------<gradient>"));
        creditsMessage.forEach(line -> sender.sendMessage(mm.deserialize(" " + line)));
        sender.sendMessage(mm.deserialize("<gradient:#7FFFD4:#20B2AA>--------------------------------------------------<gradient>"));
    }
}