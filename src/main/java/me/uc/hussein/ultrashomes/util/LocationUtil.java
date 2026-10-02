package me.uc.hussein.ultrashomes.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Validates locations coming from disk / config without silently changing the saved coordinates. */
public final class LocationUtil {
    private LocationUtil() {
    }

    public static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    /** True when the world exists, is loaded and the coordinates are finite numbers. */
    public static boolean isValid(String worldName, double x, double y, double z) {
        if (worldName == null || worldName.isBlank()) return false;
        World w = Bukkit.getWorld(worldName);
        if (w == null) return false;
        if (!isFinite(x) || !isFinite(y) || !isFinite(z)) return false;
        return y >= w.getMinHeight() - 64 && y <= w.getMaxHeight() + 64;
    }

    public static Location build(String worldName, double x, double y, double z, float yaw, float pitch) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }
}
