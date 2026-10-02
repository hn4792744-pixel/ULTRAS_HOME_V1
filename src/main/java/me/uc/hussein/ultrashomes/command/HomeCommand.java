package me.uc.hussein.ultrashomes.command;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import me.uc.hussein.ultrashomes.model.Home;
import me.uc.hussein.ultrashomes.model.HomeLookup;
import me.uc.hussein.ultrashomes.model.PlayerHomeData;
import me.uc.hussein.ultrashomes.util.LocationUtil;
import me.uc.hussein.ultrashomes.util.Text;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** /home [name | remove <name> | rest [confirm]] and /homes (no-args alias). */
public final class HomeCommand implements CommandExecutor, TabCompleter {
    private final UltrasHomesPlugin plugin;
    /** Handles "/home admin ..." (same code as /home_admin, so both forms always behave identically). */
    private final HomeAdminCommand admin;
    /** uuid -> confirmation expiry millis, for /home rest. */
    private final Map<java.util.UUID, Long> resetPending = new ConcurrentHashMap<>();

    public HomeCommand(UltrasHomesPlugin plugin, HomeAdminCommand admin) {
        this.plugin = plugin;
        this.admin = admin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.messages();
        // /home admin <add|set|rest|tp|home|setting|reload> ... - only for people with some admin permission
        // (everyone else keeps the old behaviour: "admin" is just treated like a home name).
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin") && HomeAdminCommand.hasAnyAdminPermission(sender)) {
            return admin.onCommand(sender, cmd, label, Arrays.copyOfRange(args, 1, args.length));
        }
        if (!(sender instanceof Player p)) {
            msg.send(sender, "general.player-only");
            return true;
        }
        if (!p.hasPermission("ultras.homes.home") && !p.hasPermission("ultras.homes.use")) {
            msg.send(p, "general.no-permission");
            return true;
        }
        if (plugin.cfg().worldBlacklisted(p.getWorld().getName())) {
            msg.send(p, "general.world-disabled");
            return true;
        }

        if (args.length == 0) {
            plugin.homes().withData(p.getUniqueId(), p.getName(), data -> plugin.gui().openHomes(p, data, 0));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String resetWord = plugin.cfg().resetSubcommand();
        if (sub.equals("setting") || sub.equals("settings")) {
            if (!p.hasPermission("ultras.homes.setting")) {
                msg.send(p, "general.no-permission");
                return true;
            }
            plugin.homes().withData(p.getUniqueId(), p.getName(),
                    data -> plugin.gui().openSettings(p, data, () -> plugin.gui().openHomes(p, data, 0)));
            return true;
        }
        if (sub.equals("remove") || sub.equals("delete")) {
            if (args.length < 2) {
                msg.send(p, "general.invalid-usage", Text.map("usage", "/home remove <name>"));
                return true;
            }
            String name = args[1];
            plugin.homes().withData(p.getUniqueId(), p.getName(), data -> {
                var entry = HomeLookup.byName(data, name);
                if (entry == null) {
                    msg.send(p, "home.not-found", Text.map("home", name));
                    return;
                }
                plugin.homes().removeHome(data, entry.getKey());
                if (plugin.cfg().logHomeDelete()) {
                    plugin.getLogger().info("[HOME_DELETE] " + p.getName() + " deleted " + entry.getValue().name() + " via command");
                }
                msg.send(p, "home.deleted", Text.map("home", entry.getValue().name()));
            });
            return true;
        }
        if (sub.equals(resetWord) || sub.equals("reset") || sub.equals("rest")) {
            boolean confirmArg = args.length >= 2 && args[1].equalsIgnoreCase("confirm");
            if (!plugin.cfg().resetRequiresConfirmation()) {
                doReset(p);
                return true;
            }
            if (confirmArg) {
                Long until = resetPending.remove(p.getUniqueId());
                if (until == null || until < System.currentTimeMillis()) {
                    msg.send(p, "home.reset-none-pending");
                    return true;
                }
                doReset(p);
                return true;
            }
            resetPending.put(p.getUniqueId(), System.currentTimeMillis() + plugin.cfg().confirmationTimeoutSeconds() * 1000L);
            msg.send(p, "home.reset-confirm", Text.map("seconds", String.valueOf(plugin.cfg().confirmationTimeoutSeconds())));
            return true;
        }

        // /home <name> -> teleport
        String name = args[0];
        plugin.homes().withData(p.getUniqueId(), p.getName(), data -> {
            var entry = HomeLookup.byName(data, name);
            if (entry == null) {
                if (data.homes.isEmpty()) {
                    msg.send(p, "home.no-homes");
                } else {
                    msg.send(p, "home.not-found", Text.map("home", name));
                }
                return;
            }
            Home h = entry.getValue();
            if (plugin.cfg().worldBlacklisted(h.world())) {
                msg.send(p, "teleport.world-blocked");
                return;
            }
            Location dest = LocationUtil.build(h.world(), h.x(), h.y(), h.z(), h.yaw(), h.pitch());
            if (dest == null) {
                msg.send(p, "teleport.invalid-location");
                return;
            }
            if (plugin.cfg().logHomeTeleport()) {
                plugin.getLogger().info("[HOME_TELEPORT] " + p.getName() + " -> " + h.name() + " via command");
            }
            plugin.teleport().start(p, dest, h.name(), () -> LocationUtil.isValid(h.world(), h.x(), h.y(), h.z()));
        });
        return true;
    }

    private void doReset(Player p) {
        plugin.homes().withData(p.getUniqueId(), p.getName(), data -> {
            plugin.homes().clearHomes(data);
            if (plugin.cfg().logHomeDelete()) {
                plugin.getLogger().info("[HOME_DELETE] " + p.getName() + " reset all homes via command");
            }
            plugin.messages().send(p, "home.reset-done");
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player p)) {
            // console: only "/home admin ..." makes sense
            if (args.length == 1) return filter(List.of("admin"), args[0]);
            if (args.length > 1 && args[0].equalsIgnoreCase("admin")) return admin.onTabComplete(sender, cmd, alias, Arrays.copyOfRange(args, 1, args.length));
            return out;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("admin") && HomeAdminCommand.hasAnyAdminPermission(sender)) {
            return admin.onTabComplete(sender, cmd, alias, Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 1) {
            out.add("remove");
            out.add(plugin.cfg().resetSubcommand());
            if (p.hasPermission("ultras.homes.setting")) out.add("setting");
            if (HomeAdminCommand.hasAnyAdminPermission(sender)) out.add("admin");
            PlayerHomeData cached = plugin.homes().peek(p.getUniqueId());
            if (cached != null) {
                for (Home h : cached.homes.values()) out.add(h.name());
            }
            return filter(out, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("delete"))) {
            PlayerHomeData cached = plugin.homes().peek(p.getUniqueId());
            if (cached != null) {
                for (Home h : cached.homes.values()) out.add(h.name());
            }
            return filter(out, args[1]);
        }
        return out;
    }

    private static List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(t)).distinct().sorted().toList();
    }
}
