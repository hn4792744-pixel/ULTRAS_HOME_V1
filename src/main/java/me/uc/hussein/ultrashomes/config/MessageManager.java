package me.uc.hussein.ultrashomes.config;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import me.uc.hussein.ultrashomes.util.ResourceSync;
import me.uc.hussein.ultrashomes.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads messages/en.yml and messages/ar.yml. language.default (config.yml) is the server default;
 * every player can pick their own language in /home setting, and messages sent to a player use it.
 * A player who switched "Chat Messages" OFF does not receive ordinary plugin messages (admin and
 * settings feedback is always delivered, see {@link #essential(String)}).
 */
public final class MessageManager {
    private static final List<String> LANGS = List.of("en", "ar");

    private final UltrasHomesPlugin plugin;
    private volatile Map<String, YamlConfiguration> langs = Map.of();
    private volatile String lang = "en";

    public MessageManager(UltrasHomesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        Map<String, YamlConfiguration> loaded = new HashMap<>();
        for (String l : LANGS) {
            String path = "messages/" + l + ".yml";
            ResourceSync.sync(plugin, path);
            YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), path));
            try (InputStream in = plugin.getResource(path)) {
                if (in != null) {
                    y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not read bundled " + path + ": " + e.getMessage());
            }
            loaded.put(l, y);
        }
        lang = plugin.cfg().language();
        langs = Map.copyOf(loaded);
    }

    /** The server default language (language.default). */
    public String language() {
        return lang;
    }

    /** The language a given recipient reads: their personal choice, or the server default (console etc.). */
    public String languageOf(CommandSender to) {
        if (to instanceof Player p) {
            return plugin.settings().language(p.getUniqueId());
        }
        return lang;
    }

    // ------------------------------------------------------------------ raw access
    public String raw(String language, String key) {
        for (String l : new String[]{language, lang, "en"}) {
            YamlConfiguration y = langs.get(l);
            String s = y == null ? null : y.getString(key);
            if (s != null) return s;
        }
        return key;
    }

    public String raw(String key) {
        return raw(lang, key);
    }

    public String raw(CommandSender to, String key) {
        return raw(languageOf(to), key);
    }

    public List<String> rawList(String language, String key) {
        for (String l : new String[]{language, lang, "en"}) {
            YamlConfiguration y = langs.get(l);
            List<String> list = y == null ? List.of() : y.getStringList(key);
            if (!list.isEmpty()) return list;
        }
        return List.of();
    }

    public List<String> rawList(String key) {
        return rawList(lang, key);
    }

    // ------------------------------------------------------------------ rendering
    private Map<String, String> withPrefix(Map<String, String> ph) {
        Map<String, String> m = ph == null ? new HashMap<>() : new HashMap<>(ph);
        m.putIfAbsent("prefix-mm", plugin.cfg().prefix());
        return m;
    }

    private String fillPrefix(String tpl, Map<String, String> ph) {
        String withPrefix = tpl.replace("{prefix}", "{prefix-mm}");
        return Text.fill(withPrefix, withPrefix(ph));
    }

    public Component c(String language, String key, Map<String, String> ph) {
        return Text.mm(fillPrefix(raw(language, key), ph));
    }

    public Component c(CommandSender to, String key, Map<String, String> ph) {
        return c(languageOf(to), key, ph);
    }

    public Component c(String key, Map<String, String> ph) {
        return c(lang, key, ph);
    }

    public Component c(String key) {
        return c(key, null);
    }

    public List<Component> list(CommandSender to, String key, Map<String, String> ph) {
        List<Component> out = new ArrayList<>();
        for (String line : rawList(languageOf(to), key)) {
            out.add(Text.mm(fillPrefix(line, ph)));
        }
        return out;
    }

    public List<Component> list(String key, Map<String, String> ph) {
        List<Component> out = new ArrayList<>();
        for (String line : rawList(key)) {
            out.add(Text.mm(fillPrefix(line, ph)));
        }
        return out;
    }

    public String plain(CommandSender to, String key) {
        return Text.strip(fillPrefix(raw(to, key), null));
    }

    public String plain(String key, Map<String, String> ph) {
        return Text.strip(fillPrefix(raw(key), ph));
    }

    public String plain(String key) {
        return plain(key, null);
    }

    // ------------------------------------------------------------------ sending
    /**
     * Messages that must reach the player even with "Chat Messages" OFF: anything an admin command
     * answers with (admin.*, command.admin-*, reload) and the feedback of the settings menu itself
     * (settings.*) - otherwise the toggle would give no confirmation at all.
     */
    private static boolean essential(String key) {
        return key.startsWith("admin.") || key.startsWith("settings.")
                || key.startsWith("command.admin") || key.equals("general.reload-done");
    }

    private boolean muted(CommandSender to, String key) {
        return to instanceof Player p && !essential(key) && !plugin.settings().chatEnabled(p.getUniqueId());
    }

    public void send(CommandSender to, String key, Map<String, String> ph) {
        if (muted(to, key)) return;
        to.sendMessage(c(to, key, ph));
    }

    public void send(CommandSender to, String key) {
        send(to, key, null);
    }

    public void sendList(CommandSender to, String key, Map<String, String> ph) {
        if (muted(to, key)) return;
        for (Component c : list(to, key, ph)) {
            to.sendMessage(c);
        }
    }
}
