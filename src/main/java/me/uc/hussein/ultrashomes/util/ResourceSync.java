package me.uc.hussein.ultrashomes.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Keeps messages/*.yml and gui/*.yml on disk in step with the bundled ones. Files created by an older
 * version of the plugin have no (or a lower) top-level "version" number: they are copied to
 * "&lt;name&gt;.old-vN" and replaced by the bundled file, so a plugin update really applies the new
 * layout instead of silently keeping stale/broken text. Files are never touched when versions match.
 */
public final class ResourceSync {
    private ResourceSync() {
    }

    public static void sync(JavaPlugin plugin, String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
            return;
        }
        int bundled = bundledVersion(plugin, resourcePath);
        int current = YamlConfiguration.loadConfiguration(file).getInt("version", 1);
        if (bundled <= current) return;
        File backup = new File(file.getParentFile(), file.getName() + ".old-v" + current);
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.saveResource(resourcePath, true);
            plugin.getLogger().warning(resourcePath + " was updated to layout version " + bundled
                    + " (your previous file was saved as " + backup.getName() + ").");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not update " + resourcePath + ": " + e.getMessage());
        }
    }

    private static int bundledVersion(JavaPlugin plugin, String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return 1;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)).getInt("version", 1);
        } catch (IOException e) {
            return 1;
        }
    }
}
