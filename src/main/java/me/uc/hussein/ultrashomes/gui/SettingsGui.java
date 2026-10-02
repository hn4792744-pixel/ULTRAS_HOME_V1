package me.uc.hussein.ultrashomes.gui;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import me.uc.hussein.ultrashomes.model.PlayerHomeData;
import me.uc.hussein.ultrashomes.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Personal settings menu (/home setting). When the viewer is not the owner of {@code data} (an admin
 * opened it with /home admin setting &lt;player&gt;), every change applies to that player only.
 */
public final class SettingsGui extends Menu {
    private final PlayerHomeData data;
    private final boolean adminMode;
    private final Runnable back;
    private final YamlConfiguration cfg;

    public SettingsGui(UltrasHomesPlugin plugin, Player viewer, PlayerHomeData data, Runnable back) {
        super(plugin, viewer);
        this.data = data;
        this.adminMode = !viewer.getUniqueId().equals(data.uuid);
        this.back = back;
        this.cfg = plugin.guiConfig().settings();
    }

    @Override
    protected Component title() {
        String tpl = adminMode ? cfg.getString("admin-title", "{target} - Settings") : cfg.getString("title", "Settings");
        return Text.mm(Text.fill(tpl, Text.map("target", data.name)));
    }

    @Override
    protected int size() {
        return Math.max(9, cfg.getInt("rows", 3) * 9);
    }

    @Override
    protected String openSoundKey() {
        return cfg.getString("open-sound");
    }

    private String state(boolean on) {
        return plugin.messages().plain(viewer, on ? "settings.state-on" : "settings.state-off");
    }

    private String languageName(String code) {
        return plugin.messages().plain(viewer, "settings.language-name." + code);
    }

    @Override
    protected void build() {
        var gui = plugin.gui();
        var settings = plugin.settings();
        var msg = plugin.messages();
        gui.fillBackground(inventory, cfg);

        // ---- Chat Messages ON / OFF
        ConfigurationSection chat = cfg.getConfigurationSection("chat-messages");
        if (chat != null) {
            boolean on = data.chatMessages;
            set(chat.getInt("slot", -1), gui.item(chat, on ? "on" : "off", Text.map("state", state(on), "target", data.name)), e -> {
                boolean now = settings.toggleChat(data);
                gui.playSound(viewer, chat.getString("sound"));
                if (adminMode) {
                    msg.send(viewer, "settings.admin-chat-changed", Text.map("target", data.name, "state", state(now)));
                    logAdmin("chat-messages=" + now);
                } else {
                    msg.send(viewer, now ? "settings.chat-on" : "settings.chat-off");
                }
                refresh();
            });
        }

        // ---- Plugin Sounds ON / OFF
        ConfigurationSection snd = cfg.getConfigurationSection("plugin-sounds");
        if (snd != null) {
            boolean on = data.pluginSounds;
            set(snd.getInt("slot", -1), gui.item(snd, on ? "on" : "off", Text.map("state", state(on), "target", data.name)), e -> {
                boolean now = settings.toggleSounds(data);
                // played AFTER the change: turning sounds ON confirms with a click, turning them OFF is silent
                gui.playSound(viewer, snd.getString("sound"));
                if (adminMode) {
                    msg.send(viewer, "settings.admin-sounds-changed", Text.map("target", data.name, "state", state(now)));
                    logAdmin("plugin-sounds=" + now);
                } else {
                    msg.send(viewer, now ? "settings.sounds-on" : "settings.sounds-off");
                }
                refresh();
            });
        }

        // ---- Language: English -> Arabic -> English
        ConfigurationSection lang = cfg.getConfigurationSection("language");
        if (lang != null) {
            String current = settings.language(data);
            set(lang.getInt("slot", -1), gui.item(cfg, "language", Text.map("language", languageName(current), "target", data.name)), e -> {
                String next = settings.cycleLanguage(data);
                gui.playSound(viewer, lang.getString("sound"));
                if (adminMode) {
                    msg.send(viewer, "settings.admin-language-changed", Text.map("target", data.name, "language", languageName(next)));
                    logAdmin("language=" + next);
                } else {
                    msg.send(viewer, "settings.language-changed", Text.map("language", languageName(next)));
                }
                refresh();
            });
        }

        // ---- Back
        ConfigurationSection b = cfg.getConfigurationSection("back");
        if (b != null && back != null) {
            set(b.getInt("slot", -1), gui.item(cfg, "back", null), e -> {
                gui.playSound(viewer, b.getString("sound"));
                back.run();
            });
        }
    }

    private void logAdmin(String change) {
        if (plugin.cfg().logAdminActions()) {
            plugin.getLogger().info("[ADMIN_SETTING] " + viewer.getName() + " -> " + data.name + " " + change);
        }
    }
}
