package eu.kotori.justRTP.utils;

import eu.kotori.justRTP.JustRTP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class StartupMessage {

    public static void sendStartupMessage(JustRTP plugin) {
        CommandSender console = Bukkit.getConsoleSender();
        MiniMessage mm = MiniMessage.miniMessage();

        String check = "<green>✔</green>";
        String cross = "<red>✖</red>";

        String proxyStatus = plugin.getConfigManager().getProxyEnabled() ? check : cross;
        String papiStatus = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ? check : cross;
        String worldGuardStatus = Bukkit.getPluginManager().isPluginEnabled("WorldGuard") ? check : cross;
        
        String redisStatus = "<gray>-</gray>";
        if (plugin.getConfigManager().isRedisEnabled()) {
            redisStatus = check;
        }

        String engine;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            engine = "Folia";
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("com.destroystokyo.paper.PaperConfig");
                engine = "Paper";
            } catch (ClassNotFoundException e2) {
                engine = "Spigot/Bukkit";
            }
        }

        TagResolver placeholders = TagResolver.builder()
                .resolver(Placeholder.unparsed("version", plugin.getPluginMeta().getVersion()))
                .resolver(Placeholder.unparsed("author", String.join(", ", plugin.getPluginMeta().getAuthors())))
                .build();

        String mainColor = "#20B2AA";
        String accentColor = "#7FFFD4";
        String lineSeparator = "<dark_gray><strikethrough>                                                                                ";

        console.sendMessage(mm.deserialize(lineSeparator));
        console.sendMessage(Component.empty());
        console.sendMessage(mm.deserialize("  <color:" + mainColor + ">█╗  ██╗   <white>JustRTP <gray>v<version>", placeholders));
        console.sendMessage(mm.deserialize("  <color:" + mainColor + ">██║ ██╔╝   <gray>ʙʏ <white><author>", placeholders));
        console.sendMessage(mm.deserialize("  <color:" + mainColor + ">█████╔╝    <white>sᴛᴀᴛᴜs: <color:#2ecc71>Active"));
        console.sendMessage(mm.deserialize("  <color:" + accentColor + ">█╔═██╗"));
        console.sendMessage(mm.deserialize("  <color:" + accentColor + ">█║  ██╗   <white>ᴘʀᴏxʏ sᴜᴘᴘᴏʀᴛ: " + proxyStatus));
        console.sendMessage(mm.deserialize("  <color:" + accentColor + ">█║  ╚═╝   <white>ʀᴇᴅɪs ᴄᴀᴄʜᴇ: " + redisStatus + " <gray>(optional)"));
        console.sendMessage(Component.empty());
        console.sendMessage(mm.deserialize("  <white>ᴡᴏʀʟᴅɢᴜᴀʀᴅ: " + worldGuardStatus + " <gray>| <white>ᴘᴀᴘɪ: " + papiStatus + " <gray>| <white>ᴇɴɢɪɴᴇ: <gray>" + engine));
        console.sendMessage(Component.empty());
        console.sendMessage(mm.deserialize(lineSeparator));
    }

    public static void sendUpdateNotification(JustRTP plugin) {
        CommandSender console = Bukkit.getConsoleSender();
        MiniMessage mm = MiniMessage.miniMessage();

        TagResolver placeholders = TagResolver.builder()
                .resolver(Placeholder.unparsed("current_version", plugin.getPluginMeta().getVersion()))
                .resolver(Placeholder.unparsed("latest_version", plugin.latestVersion))
                .build();

        String mainColor = "#f39c12";
        String accentColor = "#e67e22";
        String lineSeparator = "<dark_gray><strikethrough>                                                                                ";

        List<String> updateBlock = List.of(
                "  <color:" + mainColor + ">█╗  ██╗   <white>JustRTP <gray>Update",
                "  <color:" + mainColor + ">██║ ██╔╝   <gray>A new version is available!",
                "  <color:" + mainColor + ">█████╔╝",
                "  <color:" + accentColor + ">█╔═██╗    <white>ᴄᴜʀʀᴇɴᴛ: <gray><current_version>",
                "  <color:" + accentColor + ">█║  ██╗   <white>ʟᴀᴛᴇsᴛ: <green><latest_version>",
                "  <color:" + accentColor + ">█║  ╚═╝   <aqua><click:open_url:'https://builtbybit.com/resources/justrtp-lightweight-fast-randomtp.70322/'>Click here to download</click>",
                ""
        );

        console.sendMessage(mm.deserialize(lineSeparator));
        console.sendMessage(Component.empty());
        for (String line : updateBlock) {
            console.sendMessage(mm.deserialize(line, placeholders));
        }
        console.sendMessage(mm.deserialize(lineSeparator));
    }

    public static void sendUpdateNotification(Player player, JustRTP plugin) {
        MiniMessage mm = MiniMessage.miniMessage();
        String link = "https://builtbybit.com/resources/justrtp-lightweight-fast-randomtp.70322/";
        List<String> updateMessage = Arrays.asList(
                "",
                "  <gradient:#20B2AA:#7FFFD4>JustRTP</gradient> <gray>Update Available!</gray>",
                "  <gray>A new version is available: <green><latest_version></green>",
                "  <click:open_url:'" + link + "'><hover:show_text:'<green>Click to visit download page!'><#7FFFD4><u>Click here to download the update.</u></hover></click>",
                ""
        );

        player.sendMessage(mm.deserialize("<gradient:#20B2AA:#7FFFD4>--------------------------------------------------</gradient>"));
        updateMessage.forEach(line -> player.sendMessage(mm.deserialize(line, Placeholder.unparsed("latest_version", plugin.latestVersion))));
        player.sendMessage(mm.deserialize("<gradient:#7FFFD4:#20B2AA>--------------------------------------------------</gradient>"));
    }
}