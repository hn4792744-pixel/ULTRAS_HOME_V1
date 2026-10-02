package me.uc.hussein.ultrashomes.config;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Wraps config.yml with cached hot-path values and safe defaults. */
public final class ConfigManager {
    private final UltrasHomesPlugin plugin;

    private int defaultLimit = 4;
    private int maximumHomes = 50;
    private String nameFormat = "home{number}";
    private boolean resetRequiresConfirmation = true;
    private int confirmationTimeoutSeconds = 30;
    private String resetSubcommand = "rest";

    private int teleportDelay = 3;
    private int teleportCooldown = 10;
    private boolean cancelOnMove = true;
    private double moveTolerance = 0.35;
    private boolean protectionEnabled = true;
    private int protectionDuration = 2;

    private boolean adminBypassCooldown = true;
    private String setLimitBehavior = "KEEP";
    private boolean resetHomesToo = false;

    private boolean worldBlacklistEnabled = false;
    private Set<String> blacklistedWorlds = Set.of();

    private boolean loggingEnabled = true;

    public ConfigManager(UltrasHomesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        defaultLimit = Math.max(0, c.getInt("homes.default-limit", 4));
        maximumHomes = Math.max(defaultLimit, c.getInt("homes.maximum", 50));
        nameFormat = c.getString("homes.name-format", "home{number}");
        if (nameFormat == null || nameFormat.isBlank() || !nameFormat.contains("{number}")) {
            plugin.getLogger().warning("homes.name-format is invalid, falling back to 'home{number}'.");
            nameFormat = "home{number}";
        }
        resetRequiresConfirmation = c.getBoolean("homes.confirmation.reset-requires-confirmation", true);
        confirmationTimeoutSeconds = Math.max(5, c.getInt("homes.confirmation.confirmation-timeout-seconds", 30));
        String rsc = c.getString("homes.reset-subcommand", "rest");
        resetSubcommand = (rsc == null || rsc.isBlank()) ? "rest" : rsc.toLowerCase(Locale.ROOT);

        teleportDelay = Math.max(0, c.getInt("teleport.delay", 3));
        teleportCooldown = Math.max(0, c.getInt("teleport.cooldown", 10));
        cancelOnMove = c.getBoolean("teleport.cancel-on-move", true);
        moveTolerance = Math.max(0.05, c.getDouble("teleport.move-tolerance", 0.35));
        protectionEnabled = c.getBoolean("teleport.arrival-protection.enabled", true);
        protectionDuration = Math.max(0, c.getInt("teleport.arrival-protection.duration", 2));

        adminBypassCooldown = c.getBoolean("admin.bypass-cooldown", true);
        String slb = c.getString("admin.set-limit-behavior", "KEEP");
        setLimitBehavior = ("DELETE_EXCESS".equalsIgnoreCase(slb)) ? "DELETE_EXCESS" : "KEEP";
        resetHomesToo = c.getBoolean("admin.reset-homes-too", false);

        worldBlacklistEnabled = c.getBoolean("world-blacklist.enabled", false);
        List<String> worlds = c.getStringList("world-blacklist.worlds");
        Set<String> set = new HashSet<>();
        for (String w : worlds) {
            if (w != null && !w.isBlank()) set.add(w.trim());
        }
        blacklistedWorlds = Set.copyOf(set);

        loggingEnabled = c.getBoolean("logging.enabled", true);
    }

    public FileConfiguration get() {
        return plugin.getConfig();
    }

    public String language() {
        String l = plugin.getConfig().getString("language.default", "en");
        l = l == null ? "en" : l.toLowerCase(Locale.ROOT).trim();
        return l.equals("ar") ? "ar" : "en";
    }

    public String prefix() {
        return plugin.getConfig().getString("prefix", "<gradient:#8B0000:#FF5555><bold>ULTRAS</bold></gradient> <dark_gray>|</dark_gray> <gray>");
    }

    public int defaultLimit() { return defaultLimit; }
    public int maximumHomes() { return maximumHomes; }
    public String nameFormat() { return nameFormat; }
    public boolean resetRequiresConfirmation() { return resetRequiresConfirmation; }
    public int confirmationTimeoutSeconds() { return confirmationTimeoutSeconds; }
    public String resetSubcommand() { return resetSubcommand; }

    public int teleportDelay() { return teleportDelay; }
    public int teleportCooldown() { return teleportCooldown; }
    public boolean cancelOnMove() { return cancelOnMove; }
    public double moveTolerance() { return moveTolerance; }
    public boolean protectionEnabled() { return protectionEnabled; }
    public int protectionDuration() { return protectionDuration; }

    public boolean adminBypassCooldown() { return adminBypassCooldown; }
    public boolean deleteExcessOnSet() { return "DELETE_EXCESS".equals(setLimitBehavior); }
    public boolean resetHomesToo() { return resetHomesToo; }

    public boolean worldBlacklisted(String world) {
        return worldBlacklistEnabled && world != null && blacklistedWorlds.contains(world);
    }

    public boolean loggingEnabled() { return loggingEnabled; }
    public boolean logHomeCreate() { return loggingEnabled && plugin.getConfig().getBoolean("logging.log-home-create", true); }
    public boolean logHomeDelete() { return loggingEnabled && plugin.getConfig().getBoolean("logging.log-home-delete", true); }
    public boolean logHomeTeleport() { return loggingEnabled && plugin.getConfig().getBoolean("logging.log-home-teleport", true); }
    public boolean logAdminActions() { return loggingEnabled && plugin.getConfig().getBoolean("logging.log-admin-actions", true); }
    public boolean logData() { return loggingEnabled && plugin.getConfig().getBoolean("logging.log-data", false); }

    public boolean playerInfoEnabled() { return plugin.getConfig().getBoolean("player-info.enabled", true); }
    public boolean playerInfoShowWorld() { return plugin.getConfig().getBoolean("player-info.show-world", true); }
    public boolean playerInfoShowCoordinates() { return plugin.getConfig().getBoolean("player-info.show-coordinates", true); }
    public boolean playerInfoShowOnline() { return plugin.getConfig().getBoolean("player-info.show-online-status", true); }

    public boolean actionBarEnabled() { return plugin.getConfig().getBoolean("teleport.actionbar.enabled", true); }
    /** 0 = show the whole countdown; N > 0 = only show the action bar when N seconds or less remain. */
    public int actionBarShowFrom() { return Math.max(0, plugin.getConfig().getInt("teleport.actionbar.show-from-seconds", 0)); }

    public boolean overrideConflicts() { return plugin.getConfig().getBoolean("commands.override-conflicts", true); }
    public boolean hideConflictsFromTab() { return plugin.getConfig().getBoolean("commands.hide-conflicts-from-tab", true); }
    public boolean hideAliasesFromTab() { return plugin.getConfig().getBoolean("commands.hide-aliases-from-tab", true); }

    public long saveIntervalTicks() {
        return Math.max(5, plugin.getConfig().getInt("storage.save-interval-seconds", 60)) * 20L;
    }

    public long unloadDelayTicks() {
        return Math.max(5, plugin.getConfig().getInt("storage.unload-delay-seconds", 30)) * 20L;
    }

    public String homeName(int number) {
        return nameFormat.replace("{number}", String.valueOf(number));
    }
}
