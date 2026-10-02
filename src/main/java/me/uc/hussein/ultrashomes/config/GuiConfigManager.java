package me.uc.hussein.ultrashomes.config;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import me.uc.hussein.ultrashomes.util.ResourceSync;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads the gui/*.yml files so every GUI's layout, materials, text and sounds are data-driven. */
public final class GuiConfigManager {
    public static final String HOMES = "homes.yml";
    public static final String HOME_MANAGE = "home_manage.yml";
    public static final String PLAYER_HOMES = "player_homes.yml";
    public static final String ADMIN_HOMES = "admin_homes.yml";
    public static final String SETTINGS = "settings.yml";

    private final UltrasHomesPlugin plugin;
    private final Map<String, YamlConfiguration> cache = new HashMap<>();

    public GuiConfigManager(UltrasHomesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        cache.clear();
        for (String f : new String[]{HOMES, HOME_MANAGE, PLAYER_HOMES, ADMIN_HOMES, SETTINGS}) {
            cache.put(f, loadOne(f));
        }
        validateHomeList(HOMES, new String[]{"info", "settings-button", "navigation.previous", "navigation.next", "navigation.close"});
        validateHomeList(PLAYER_HOMES, new String[]{"player-head", "settings-button", "limit-add", "limit-remove",
                "navigation.previous", "navigation.next", "navigation.close"});
    }

    private YamlConfiguration loadOne(String name) {
        String resourcePath = "gui/" + name;
        ResourceSync.sync(plugin, resourcePath);
        File file = new File(plugin.getDataFolder(), resourcePath);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in != null) {
                y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read bundled " + resourcePath + ": " + e.getMessage());
        }
        return y;
    }

    /** Warns (never throws) about slots that collide, fall outside the inventory, or homes-per-page that cannot fit. */
    private void validateHomeList(String file, String[] extraSections) {
        YamlConfiguration y = cache.get(file);
        int size = Math.max(9, y.getInt("rows", 4) * 9);
        Map<Integer, String> used = new HashMap<>();
        List<Integer> beds = y.getIntegerList("bed-slots");
        List<Integer> dyes = y.getIntegerList("dye-slots");
        for (int s : beds) claim(file, used, size, s, "bed-slots");
        for (int s : dyes) claim(file, used, size, s, "dye-slots");
        for (String section : extraSections) {
            ConfigurationSection sec = y.getConfigurationSection(section);
            if (sec != null && sec.contains("slot")) claim(file, used, size, sec.getInt("slot"), section);
        }
        int fit = Math.min(beds.size(), dyes.size());
        int perPage = y.getInt("homes-per-page");
        if (perPage > fit) {
            plugin.getLogger().warning("gui/" + file + ": homes-per-page (" + perPage + ") is larger than the number of bed-slots/dye-slots ("
                    + fit + "); only " + fit + " homes will be shown per page.");
        }
        if (beds.size() != dyes.size()) {
            plugin.getLogger().warning("gui/" + file + ": bed-slots and dye-slots should have the same number of entries.");
        }
    }

    private void claim(String file, Map<Integer, String> used, int size, int slot, String owner) {
        if (slot < 0 || slot >= size) {
            plugin.getLogger().warning("gui/" + file + ": slot " + slot + " of '" + owner + "' is outside the inventory (0-" + (size - 1) + ").");
            return;
        }
        String prev = used.putIfAbsent(slot, owner);
        if (prev != null) {
            plugin.getLogger().warning("gui/" + file + ": slot " + slot + " is used by both '" + prev + "' and '" + owner + "'.");
        }
    }

    public YamlConfiguration homes() { return cache.get(HOMES); }
    public YamlConfiguration homeManage() { return cache.get(HOME_MANAGE); }
    public YamlConfiguration playerHomes() { return cache.get(PLAYER_HOMES); }
    public YamlConfiguration adminHomes() { return cache.get(ADMIN_HOMES); }
    public YamlConfiguration settings() { return cache.get(SETTINGS); }
}
