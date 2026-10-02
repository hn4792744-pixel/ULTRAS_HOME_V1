package me.uc.hussein.ultrashomes.command;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.ServerLoadEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Makes ULTRAS_HOMES' own commands (home, homes, sethome, home_admin and their aliases) the ones that
 * run when another plugin registered the same name, and keeps the Tab list clean.
 *
 * <ul>
 *   <li>The other plugin's command is NOT unregistered or disabled: only the plain label points to ours.
 *       The other plugin keeps working and its command stays reachable with its own prefix
 *       (for example /essentials:home), so nothing else on the server breaks.</li>
 *   <li>Tab: our commands show up (admin command only for people allowed to use it); the prefixed
 *       duplicates and our long aliases are hidden. Options live under "commands:" in config.yml.</li>
 * </ul>
 */
public final class CommandPriorityManager implements Listener {
    private static final String ADMIN_COMMAND = "home_admin";

    private final UltrasHomesPlugin plugin;
    /** every label (main name or alias, lower-case) -> our command object */
    private final Map<String, PluginCommand> owned = new LinkedHashMap<>();
    /** labels that are aliases and not the main command name */
    private final Set<String> aliasLabels = new HashSet<>();
    /** label -> the other plugin's command we replaced under that plain label */
    private final Map<String, Command> displaced = new HashMap<>();
    /** true once the server re-sent its command tree (so clients and the dispatcher use the new owner) */
    private boolean synced;

    public CommandPriorityManager(UltrasHomesPlugin plugin) {
        this.plugin = plugin;
        for (String name : new String[]{"home", "homes", "sethome", ADMIN_COMMAND}) {
            PluginCommand pc = plugin.getCommand(name);
            if (pc == null) continue;
            owned.put(pc.getName().toLowerCase(Locale.ROOT), pc);
            for (String alias : pc.getAliases()) {
                String a = alias.toLowerCase(Locale.ROOT);
                owned.put(a, pc);
                aliasLabels.add(a);
            }
        }
    }

    /** Takes the plain labels over. Safe to call any number of times (does nothing when already correct). */
    public void apply() {
        if (owned.isEmpty() || !plugin.cfg().overrideConflicts()) return;
        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        boolean changed = false;
        for (Map.Entry<String, PluginCommand> e : owned.entrySet()) {
            String label = e.getKey();
            PluginCommand mine = e.getValue();
            Command current = known.get(label);
            if (current == mine) continue;
            if (current != null && !(current instanceof PluginCommand pc && pc.getPlugin() == plugin)) {
                displaced.putIfAbsent(label, current);
                plugin.getLogger().info("Command /" + label + " of " + describe(current)
                        + " is overridden by ULTRAS_HOMES (that plugin is untouched; its command is still available under its own prefix).");
            }
            known.put(label, mine);
            changed = true;
        }
        if (changed) sync();
    }

    /** Puts the other plugins' commands back (only where our command is still the one registered). */
    public void restore() {
        if (displaced.isEmpty()) return;
        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        for (Map.Entry<String, Command> e : displaced.entrySet()) {
            if (known.get(e.getKey()) == owned.get(e.getKey())) {
                known.put(e.getKey(), e.getValue());
            }
        }
        displaced.clear();
    }

    private static String describe(Command c) {
        return c instanceof PluginCommand pc ? pc.getPlugin().getName() : c.getClass().getSimpleName();
    }

    /** Re-sends the command tree; falls back to {@link #onPreprocess} when the server does not expose syncCommands(). */
    private void sync() {
        synced = false;
        try {
            Method m = Bukkit.getServer().getClass().getMethod("syncCommands");
            m.invoke(Bukkit.getServer());
            synced = true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().fine("syncCommands() is not available; using the command-preprocess fallback: " + ex);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.updateCommands();
        }
    }

    // ------------------------------------------------------------------ events
    /** All plugins are enabled by now (startup and /reload): claim the labels. */
    @EventHandler
    public void onServerLoad(ServerLoadEvent e) {
        apply();
    }

    /**
     * Fallback only (when syncCommands() could not be called): the Brigadier tree may still point at the
     * other plugin's command, so run ours directly for the labels we took over.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreprocess(PlayerCommandPreprocessEvent e) {
        if (synced || displaced.isEmpty()) return;
        String msg = e.getMessage();
        if (msg.length() < 2) return;
        String body = msg.substring(1);
        int space = body.indexOf(' ');
        String label = (space < 0 ? body : body.substring(0, space)).toLowerCase(Locale.ROOT);
        if (!displaced.containsKey(label)) return;
        PluginCommand mine = owned.get(label);
        if (mine == null) return;
        String rest = space < 0 ? "" : body.substring(space + 1).trim();
        e.setCancelled(true);
        mine.execute(e.getPlayer(), label, rest.isEmpty() ? new String[0] : rest.split("\\s+"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent e) {
        boolean hideConflicts = plugin.cfg().hideConflictsFromTab();
        boolean hideAliases = plugin.cfg().hideAliasesFromTab();
        boolean admin = HomeAdminCommand.hasAnyAdminPermission(e.getPlayer());
        e.getCommands().removeIf(name -> {
            String lower = name.toLowerCase(Locale.ROOT);
            int colon = lower.indexOf(':');
            String base = colon >= 0 ? lower.substring(colon + 1) : lower;
            PluginCommand ours = owned.get(base);
            if (ours == null) return false;
            if (colon >= 0) return hideConflicts;                       // "essentials:home", "ultras_homes:home" ...
            if (hideAliases && aliasLabels.contains(lower)) return true; // uhome, usethome, homeadmin ... still work when typed
            return ours.getName().equals(ADMIN_COMMAND) && !admin;       // home_admin only for admins
        });
    }
}
