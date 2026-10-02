package me.uc.hussein.ultrashomes.settings;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import me.uc.hussein.ultrashomes.model.PlayerHomeData;
import me.uc.hussein.ultrashomes.util.SoundUtil;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/**
 * Per-player personal settings (chat messages ON/OFF, plugin sounds ON/OFF, language).
 * The values live in {@link PlayerHomeData} so they are stored by the existing HomeStorage in the
 * player's own file and survive restarts / reloads. Reading never touches the disk: it only looks at
 * the in-memory copy (loaded on join) and falls back to the defaults when it is not loaded yet.
 */
public final class SettingsManager {
    public static final String EN = "en";
    public static final String AR = "ar";

    private final UltrasHomesPlugin plugin;

    public SettingsManager(UltrasHomesPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return "en" / "ar", or null for anything else (= no personal language). */
    public static String normalize(String language) {
        if (language == null) return null;
        String l = language.trim().toLowerCase(Locale.ROOT);
        return l.equals(EN) || l.equals(AR) ? l : null;
    }

    public boolean chatEnabled(UUID id) {
        PlayerHomeData d = plugin.homes().peek(id);
        return d == null || d.chatMessages;
    }

    public boolean soundsEnabled(UUID id) {
        PlayerHomeData d = plugin.homes().peek(id);
        return d == null || d.pluginSounds;
    }

    /** The language this player sees messages in: their own choice, otherwise language.default. */
    public String language(UUID id) {
        return language(plugin.homes().peek(id));
    }

    public String language(PlayerHomeData data) {
        if (data != null && data.language != null) return data.language;
        return plugin.cfg().language();
    }

    /** @return the new state */
    public boolean toggleChat(PlayerHomeData data) {
        data.chatMessages = !data.chatMessages;
        plugin.homes().markDirty(data);
        return data.chatMessages;
    }

    /** @return the new state */
    public boolean toggleSounds(PlayerHomeData data) {
        data.pluginSounds = !data.pluginSounds;
        plugin.homes().markDirty(data);
        return data.pluginSounds;
    }

    /** English -> Arabic -> English. @return the new language code */
    public String cycleLanguage(PlayerHomeData data) {
        String next = EN.equals(language(data)) ? AR : EN;
        data.language = next;
        plugin.homes().markDirty(data);
        return next;
    }

    /** Plays a plugin sound to this player only, unless they switched plugin sounds OFF. */
    public void play(Player player, String soundName, float volume, float pitch) {
        if (player == null || soundName == null || soundName.isBlank()) return;
        if (!soundsEnabled(player.getUniqueId())) return;
        SoundUtil.play(player, soundName, volume, pitch, plugin.getLogger());
    }
}
