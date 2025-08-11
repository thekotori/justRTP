package eu.kotori.justRTP.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class Cuboid {
    private final int x1, y1, z1;
    private final int x2, y2, z2;
    private final String worldName;

    public Cuboid(Location loc1, Location loc2) {
        if (!loc1.getWorld().equals(loc2.getWorld())) {
            throw new IllegalArgumentException("Locations must be in the same world");
        }
        this.worldName = loc1.getWorld().getName();
        this.x1 = Math.min(loc1.getBlockX(), loc2.getBlockX());
        this.y1 = Math.min(loc1.getBlockY(), loc2.getBlockY());
        this.z1 = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
        this.x2 = Math.max(loc1.getBlockX(), loc2.getBlockX());
        this.y2 = Math.max(loc1.getBlockY(), loc2.getBlockY());
        this.z2 = Math.max(loc1.getBlockZ(), loc2.getBlockZ());
    }

    public boolean contains(Location loc) {
        return loc.getWorld().getName().equals(this.worldName) &&
                loc.getBlockX() >= x1 && loc.getBlockX() <= x2 &&
                loc.getBlockY() >= y1 && loc.getBlockY() <= y2 &&
                loc.getBlockZ() >= z1 && loc.getBlockZ() <= z2;
    }

    public Location getCenter() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, (x1 + x2) / 2.0 + 0.5, (y1 + y2) / 2.0 + 0.5, (z1 + z2) / 2.0 + 0.5);
    }

    public Location getLowerNE() {
        return new Location(Bukkit.getWorld(worldName), x1, y1, z1);
    }

    public Location getUpperSW() {
        return new Location(Bukkit.getWorld(worldName), x2, y2, z2);
    }
}