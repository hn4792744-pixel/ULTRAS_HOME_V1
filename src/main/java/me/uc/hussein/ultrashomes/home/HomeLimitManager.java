package me.uc.hussein.ultrashomes.home;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import me.uc.hussein.ultrashomes.model.PlayerHomeData;
import org.bukkit.permissions.Permissible;

import java.util.Locale;

/**
 * Computes a player's effective home limit.
 * Priority: an explicit admin override (limit-override) always wins; otherwise the limit is the
 * highest of the config default and any granted ultras.homes.limit.<N> permission. Everything is
 * capped to homes.maximum.
 */
public final class HomeLimitManager {
    private final UltrasHomesPlugin plugin;

    public HomeLimitManager(UltrasHomesPlugin plugin) {
        this.plugin = plugin;
    }

    public int permissionLimit(Permissible p) {
        int best = plugin.cfg().defaultLimit();
        if (p.hasPermission("ultras.homes.bypass.limit")) {
            return plugin.cfg().maximumHomes();
        }
        for (org.bukkit.permissions.PermissionAttachmentInfo info : p.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String node = info.getPermission().toLowerCase(Locale.ROOT);
            if (node.startsWith("ultras.homes.limit.")) {
                String num = node.substring("ultras.homes.limit.".length());
                try {
                    int n = Integer.parseInt(num);
                    if (n > best) best = n;
                } catch (NumberFormatException ignored) {
                    // not a numeric limit node
                }
            }
        }
        return Math.min(best, plugin.cfg().maximumHomes());
    }

    public int effectiveLimit(Permissible permissible, PlayerHomeData data) {
        if (data.limitOverride != null) {
            return Math.min(data.limitOverride, plugin.cfg().maximumHomes());
        }
        return permissionLimit(permissible);
    }

    public int cap(int value) {
        return Math.max(0, Math.min(value, plugin.cfg().maximumHomes()));
    }
}
