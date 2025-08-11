package eu.kotori.justRTP.handlers.hooks;
import eu.kotori.justRTP.JustRTP;
import eu.kotori.justRTP.handlers.hooks.impl.WorldGuardHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import java.util.ArrayList;
import java.util.List;
public class HookManager {
    private final JustRTP plugin;
    private final List<RegionHook> activeHooks = new ArrayList<>();
    public HookManager(JustRTP plugin) {
        this.plugin = plugin;
        detectAndEnableHooks();
    }
    private void detectAndEnableHooks() {
        if (plugin.getConfig().getBoolean("settings.respect_regions") && isPluginEnabled("WorldGuard")) {
            activeHooks.add(new WorldGuardHook());
            plugin.getLogger().info("Successfully hooked into WorldGuard.");
        }
    }
    public boolean isLocationSafe(Location location) {
        for (RegionHook hook : activeHooks) {
            if (!hook.isLocationSafe(location)) return false;
        }
        return true;
    }
    private boolean isPluginEnabled(String pluginName) {
        Plugin pl = Bukkit.getPluginManager().getPlugin(pluginName);
        return pl != null && pl.isEnabled();
    }
}