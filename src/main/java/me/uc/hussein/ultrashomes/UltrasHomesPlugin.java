package me.uc.hussein.ultrashomes;

import me.uc.hussein.ultrashomes.command.CommandPriorityManager;
import me.uc.hussein.ultrashomes.command.HomeAdminCommand;
import me.uc.hussein.ultrashomes.command.HomeCommand;
import me.uc.hussein.ultrashomes.command.SetHomeCommand;
import me.uc.hussein.ultrashomes.config.ConfigManager;
import me.uc.hussein.ultrashomes.config.GuiConfigManager;
import me.uc.hussein.ultrashomes.config.MessageManager;
import me.uc.hussein.ultrashomes.gui.GuiManager;
import me.uc.hussein.ultrashomes.home.HomeLimitManager;
import me.uc.hussein.ultrashomes.home.HomeManager;
import me.uc.hussein.ultrashomes.listener.GuiListener;
import me.uc.hussein.ultrashomes.listener.PlayerListener;
import me.uc.hussein.ultrashomes.listener.TeleportListener;
import me.uc.hussein.ultrashomes.settings.SettingsManager;
import me.uc.hussein.ultrashomes.teleport.TeleportManager;
import me.uc.hussein.ultrashomes.util.SoundUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * ULTRAS_HOMES by UC_Hussein - GUI-driven per-player homes with teleport safety and admin tools.
 * No player data is loaded at startup: everything is lazy-loaded the first time it is needed.
 */
public final class UltrasHomesPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private GuiConfigManager guiConfigManager;
    private HomeManager homeManager;
    private HomeLimitManager homeLimitManager;
    private TeleportManager teleportManager;
    private GuiManager guiManager;
    private SettingsManager settingsManager;
    private CommandPriorityManager commandPriority;

    @Override
    public void onEnable() {
        try {
            configManager = new ConfigManager(this);
            configManager.load();
            messageManager = new MessageManager(this);
            messageManager.load();
            guiConfigManager = new GuiConfigManager(this);
            guiConfigManager.load();

            homeManager = new HomeManager(this);
            settingsManager = new SettingsManager(this);
            homeLimitManager = new HomeLimitManager(this);
            teleportManager = new TeleportManager(this);
            guiManager = new GuiManager(this);

            homeManager.start();

            var pm = getServer().getPluginManager();
            pm.registerEvents(new PlayerListener(this), this);
            pm.registerEvents(new TeleportListener(this), this);
            pm.registerEvents(new GuiListener(), this);

            // one HomeAdminCommand instance serves both /home_admin ... and /home admin ...
            HomeAdminCommand admin = new HomeAdminCommand(this);
            bind("home", new HomeCommand(this, admin));
            bind("homes", new SetHomeCommand(this, "ultras.homes.home"));
            bind("sethome", new SetHomeCommand(this, "ultras.homes.sethome"));
            PluginCommand adminCmd = getCommand("home_admin");
            if (adminCmd != null) {
                adminCmd.setExecutor(admin);
                adminCmd.setTabCompleter(admin);
            } else {
                getLogger().severe("Command missing from plugin.yml: home_admin");
            }

            // our commands win over same-named commands of other plugins (without touching those plugins) + clean Tab list
            commandPriority = new CommandPriorityManager(this);
            pm.registerEvents(commandPriority, this);
            commandPriority.apply();
            getServer().getScheduler().runTask(this, () -> commandPriority.apply());

            getLogger().info("ULTRAS_HOMES v" + getPluginMeta().getVersion() + " by UC_Hussein enabled. Language: " + messageManager.language());
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "ULTRAS_HOMES failed to start and will be disabled", t);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand c = getCommand(name);
        if (c == null) {
            getLogger().severe("Command missing from plugin.yml: " + name);
            return;
        }
        c.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tc) {
            c.setTabCompleter(tc);
        }
    }

    @Override
    public void onDisable() {
        try {
            if (teleportManager != null) teleportManager.shutdown();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Error while cancelling teleport tasks on shutdown", t);
        }
        try {
            if (commandPriority != null) commandPriority.restore();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Error while restoring other plugins' commands on shutdown", t);
        }
        try {
            if (homeManager != null) homeManager.stop();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Error while saving home data on shutdown", t);
        }
    }

    /**
     * /home_admin reload (= /home admin reload) - re-reads config, messages, GUI files and sounds without
     * leaking listeners/tasks/commands. Players' personal settings live in their data files and are kept.
     */
    public void reloadAll() {
        configManager.load();
        messageManager.load();
        guiConfigManager.load();
        SoundUtil.clearCache();
        homeManager.flushDirty(true);
        homeManager.start();
        if (commandPriority != null) commandPriority.apply();
        getLogger().info("Configuration, messages and GUI files reloaded by an admin.");
    }

    public ConfigManager cfg() { return configManager; }
    public MessageManager messages() { return messageManager; }
    public GuiConfigManager guiConfig() { return guiConfigManager; }
    public HomeManager homes() { return homeManager; }
    public HomeLimitManager limits() { return homeLimitManager; }
    public TeleportManager teleport() { return teleportManager; }
    public GuiManager gui() { return guiManager; }
    public SettingsManager settings() { return settingsManager; }
}
