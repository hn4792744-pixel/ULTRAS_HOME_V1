package me.uc.hussein.ultrashomes.home;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import me.uc.hussein.ultrashomes.model.Home;
import me.uc.hussein.ultrashomes.model.PlayerHomeData;
import me.uc.hussein.ultrashomes.settings.SettingsManager;
import me.uc.hussein.ultrashomes.util.LocationUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Reads/writes one player's YAML file. Every player has their own file under data/homes/.
 * Writing is atomic (temp file + rename, with a .bak safety copy) so a crash mid-write never
 * corrupts a player's homes. This class does not do any caching by itself - HomeManager owns that.
 */
public final class HomeStorage {
    private final UltrasHomesPlugin plugin;
    private final Path dir;

    public HomeStorage(UltrasHomesPlugin plugin) {
        this.plugin = plugin;
        this.dir = plugin.getDataFolder().toPath().resolve("data").resolve("homes");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create data/homes: " + e.getMessage());
        }
    }

    private Path fileOf(UUID uuid) {
        return dir.resolve(uuid + ".yml");
    }

    public boolean exists(UUID uuid) {
        return Files.exists(fileOf(uuid));
    }

    /** Blocking read - call from an async thread when loading on demand, never from the main thread if avoidable. */
    public PlayerHomeData load(UUID uuid, String fallbackName) {
        Path path = fileOf(uuid);
        PlayerHomeData data = new PlayerHomeData(uuid, fallbackName);
        if (!Files.exists(path)) {
            return data;
        }
        YamlConfiguration y = readYaml(path);
        if (y == null) {
            plugin.getLogger().severe("Home file for " + uuid + " is unreadable even after trying the backup. Starting empty for this player (the broken file was kept as .corrupt).");
            return data;
        }
        data.name = y.getString("name", fallbackName);
        if (y.contains("limit-override")) {
            data.limitOverride = y.getInt("limit-override");
        }
        ConfigurationSection settings = y.getConfigurationSection("settings");
        if (settings != null) {
            data.chatMessages = settings.getBoolean("chat-messages", true);
            data.pluginSounds = settings.getBoolean("plugin-sounds", true);
            data.language = SettingsManager.normalize(settings.getString("language"));
        }
        ConfigurationSection homes = y.getConfigurationSection("homes");
        if (homes != null) {
            for (String key : homes.getKeys(false)) {
                ConfigurationSection h = homes.getConfigurationSection(key);
                if (h == null) continue;
                try {
                    int number = h.getInt("number", parseNumber(key));
                    String name = h.getString("name", key);
                    String world = h.getString("world");
                    double x = h.getDouble("x");
                    double y2 = h.getDouble("y");
                    double z = h.getDouble("z");
                    float yaw = (float) h.getDouble("yaw");
                    float pitch = (float) h.getDouble("pitch");
                    long created = h.getLong("created-at", System.currentTimeMillis());
                    long updated = h.getLong("updated-at", created);
                    if (number <= 0 || world == null || world.isBlank() || !LocationUtil.isFinite(x) || !LocationUtil.isFinite(y2) || !LocationUtil.isFinite(z)) {
                        plugin.getLogger().warning("Skipping corrupt home entry '" + key + "' in " + uuid + ".yml (invalid data).");
                        continue;
                    }
                    data.homes.put(number, new Home(number, name, world, x, y2, z, yaw, pitch, created, updated));
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Skipping corrupt home entry '" + key + "' in " + uuid + ".yml: " + ex.getMessage());
                }
            }
        }
        return data;
    }

    private static int parseNumber(String key) {
        try {
            return Integer.parseInt(key.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Immutable snapshot of a player's data, safe to hand to an async writer thread. */
    public record Snapshot(UUID uuid, String name, Integer limitOverride,
                           boolean chatMessages, boolean pluginSounds, String language,
                           Map<Integer, Home> homes) {
        public Snapshot {
            homes = Map.copyOf(homes);
        }
    }

    /** Call from the main thread: cheap, only copies references. */
    public static Snapshot snapshot(PlayerHomeData data) {
        return new Snapshot(data.uuid, data.name, data.limitOverride,
                data.chatMessages, data.pluginSounds, data.language, data.homes);
    }

    /** Blocking write - safe to call from an async thread with a snapshot built on the main thread. */
    public void save(Snapshot data) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("uuid", data.uuid().toString());
        y.set("name", data.name());
        if (data.limitOverride() != null) {
            y.set("limit-override", data.limitOverride());
        }
        ConfigurationSection settings = y.createSection("settings");
        settings.set("chat-messages", data.chatMessages());
        settings.set("plugin-sounds", data.pluginSounds());
        if (data.language() != null) {
            settings.set("language", data.language());
        }
        ConfigurationSection homes = y.createSection("homes");
        // stable, sorted order for readable diffs when edited by hand
        for (Map.Entry<Integer, Home> e : new TreeMap<>(data.homes()).entrySet()) {
            Home h = e.getValue();
            ConfigurationSection s = homes.createSection(h.name());
            s.set("number", h.number());
            s.set("name", h.name());
            s.set("world", h.world());
            s.set("x", h.x());
            s.set("y", h.y());
            s.set("z", h.z());
            s.set("yaw", h.yaw());
            s.set("pitch", h.pitch());
            s.set("created-at", h.createdAt());
            s.set("updated-at", h.updatedAt());
        }
        try {
            writeAtomic(fileOf(data.uuid()), y.saveToString());
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save homes for " + data.uuid() + ": " + e.getMessage());
        }
    }

    public void delete(UUID uuid) {
        try {
            Files.deleteIfExists(fileOf(uuid));
            Files.deleteIfExists(fileOf(uuid).resolveSibling(uuid + ".yml.bak"));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not delete home file for " + uuid + ": " + e.getMessage());
        }
    }

    /** Every UUID that has a home file on disk, without loading their contents. Used by admin "all" operations. */
    public List<UUID> allStoredUuids() {
        List<UUID> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.yml")) {
            for (Path p : ds) {
                String fn = p.getFileName().toString();
                try {
                    out.add(UUID.fromString(fn.substring(0, fn.length() - 4)));
                } catch (IllegalArgumentException ignored) {
                    // not a uuid-named file, skip
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not list data/homes: " + e.getMessage());
        }
        return out;
    }

    private void writeAtomic(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        if (Files.exists(target)) {
            Files.copy(target, target.resolveSibling(target.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private YamlConfiguration readYaml(Path p) {
        YamlConfiguration y = tryRead(p);
        if (y != null) return y;
        Path bak = p.resolveSibling(p.getFileName() + ".bak");
        if (Files.exists(bak)) {
            y = tryRead(bak);
            if (y != null) {
                plugin.getLogger().warning("Recovered " + p.getFileName() + " from its backup copy.");
                return y;
            }
        }
        try {
            Path corrupt = p.resolveSibling(p.getFileName() + ".corrupt-" + System.currentTimeMillis());
            Files.move(p, corrupt, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // best effort quarantine
        }
        return null;
    }

    private YamlConfiguration tryRead(Path p) {
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(r);
            return y;
        } catch (Exception e) {
            return null;
        }
    }
}
