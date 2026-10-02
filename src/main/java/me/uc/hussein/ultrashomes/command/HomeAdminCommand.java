package me.uc.hussein.ultrashomes.command;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import me.uc.hussein.ultrashomes.model.Home;
import me.uc.hussein.ultrashomes.model.HomeLookup;
import me.uc.hussein.ultrashomes.model.PlayerHomeData;
import me.uc.hussein.ultrashomes.util.LocationUtil;
import me.uc.hussein.ultrashomes.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /home_admin (and /home admin) add|set|rest|tp|home|setting|reload - all "all"-scoped variants use file-level bulk operations.
 */
public final class HomeAdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUB = List.of("add", "set", "rest", "tp", "home", "setting", "reload");
    private final UltrasHomesPlugin plugin;

    public HomeAdminCommand(UltrasHomesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.messages();
        if (args.length == 0) {
            msg.sendList(sender, "command.admin-help", null);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add" -> handleAddSet(sender, args, true);
            case "set" -> handleAddSet(sender, args, false);
            case "rest", "reset" -> handleRest(sender, args);
            case "tp" -> handleTp(sender, args);
            case "home", "homes" -> handleHome(sender, args);
            case "setting", "settings" -> handleSetting(sender, args);
            case "reload" -> handleReload(sender);
            default -> msg.sendList(sender, "command.admin-help", null);
        }
        return true;
    }

    // ------------------------------------------------------------------ add / set
    private void handleAddSet(CommandSender sender, String[] args, boolean add) {
        var msg = plugin.messages();
        String perm = add ? "ultras.homes.admin.add" : "ultras.homes.admin.set";
        if (!sender.hasPermission(perm) && !sender.hasPermission("ultras.homes.admin")) {
            msg.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 3) {
            msg.send(sender, "admin.target-required");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            msg.send(sender, "admin.invalid-amount");
            return;
        }
        int max = plugin.cfg().maximumHomes();

        if (args[1].equalsIgnoreCase("all")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<UUID> all = plugin.homes().everyKnownPlayer();
                int count = plugin.homes().bulkTransform(all, data -> {
                    int base = data.limitOverride != null ? data.limitOverride : plugin.cfg().defaultLimit();
                    int result = add ? base + amount : amount;
                    data.limitOverride = Math.min(result, max);
                    if (!add && plugin.cfg().deleteExcessOnSet()) {
                        data.homes.keySet().removeIf(n -> n > data.limitOverride);
                    }
                });
                Bukkit.getScheduler().runTask(plugin, () -> {
                    msg.send(sender, add ? "admin.add-done-all" : "admin.set-done-all",
                            Text.map("count", String.valueOf(count), "amount", String.valueOf(amount), "new", String.valueOf(amount)));
                    if (plugin.cfg().logAdminActions()) {
                        plugin.getLogger().info("[ADMIN_LIMIT_" + (add ? "ADD" : "SET") + "] " + sender.getName() + " -> ALL (" + count + " players), amount=" + amount);
                    }
                });
            });
            return;
        }

        String targetName = args[1];
        withTarget(sender, targetName, (uuid, name) -> plugin.homes().withData(uuid, name, data -> {
            int old = data.limitOverride != null ? data.limitOverride : plugin.limits().permissionLimit(offlineOrOnline(uuid));
            int result = add ? old + amount : amount;
            boolean capped = result > max;
            data.limitOverride = Math.min(result, max);
            plugin.homes().markDirty(data);
            int removed = 0;
            if (!add && plugin.cfg().deleteExcessOnSet()) {
                removed = plugin.homes().deleteAboveLimit(data, data.limitOverride);
            }
            if (plugin.cfg().logAdminActions()) {
                plugin.getLogger().info("[ADMIN_LIMIT_" + (add ? "ADD" : "SET") + "] " + sender.getName()
                        + " -> " + name + " old=" + old + " new=" + data.limitOverride);
            }
            msg.send(sender, add ? "admin.add-done" : "admin.set-done",
                    Text.map("target", name, "amount", String.valueOf(amount), "old", String.valueOf(old), "new", String.valueOf(data.limitOverride)));
            if (capped) {
                msg.send(sender, "admin.max-exceeded", Text.map("maximum", String.valueOf(max)));
            }
            if (!add) {
                int extra = data.homes.size() - data.limitOverride;
                if (removed > 0) {
                    msg.send(sender, "admin.exceeds-deleted", Text.map("target", name, "count", String.valueOf(removed)));
                } else if (extra > 0) {
                    msg.send(sender, "admin.exceeds-kept", Text.map("target", name));
                }
            }
        }));
    }

    // ------------------------------------------------------------------ rest (reset limit)
    private void handleRest(CommandSender sender, String[] args) {
        var msg = plugin.messages();
        if (!sender.hasPermission("ultras.homes.admin.rest") && !sender.hasPermission("ultras.homes.admin")) {
            msg.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "admin.target-required");
            return;
        }
        int def = plugin.cfg().defaultLimit();
        boolean deleteHomes = plugin.cfg().resetHomesToo();

        if (args[1].equalsIgnoreCase("all")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<UUID> all = plugin.homes().everyKnownPlayer();
                int count = plugin.homes().bulkTransform(all, data -> {
                    data.limitOverride = null;
                    if (deleteHomes) data.homes.clear();
                });
                Bukkit.getScheduler().runTask(plugin, () -> {
                    msg.send(sender, "admin.rest-done-all", Text.map("count", String.valueOf(count)));
                    if (plugin.cfg().logAdminActions()) {
                        plugin.getLogger().info("[ADMIN_LIMIT_RESET] " + sender.getName() + " -> ALL (" + count + " players), reset-homes-too=" + deleteHomes);
                    }
                });
            });
            return;
        }

        String targetName = args[1];
        withTarget(sender, targetName, (uuid, name) -> plugin.homes().withData(uuid, name, data -> {
            data.limitOverride = null;
            if (deleteHomes) plugin.homes().clearHomes(data);
            else plugin.homes().markDirty(data);
            if (plugin.cfg().logAdminActions()) {
                plugin.getLogger().info("[ADMIN_LIMIT_RESET] " + sender.getName() + " -> " + name + " reset-homes-too=" + deleteHomes);
            }
            msg.send(sender, "admin.rest-done", Text.map("target", name, "default", String.valueOf(def)));
            if (deleteHomes) {
                msg.send(sender, "admin.rest-homes-deleted", Text.map("target", name));
            }
        }));
    }

    // ------------------------------------------------------------------ tp
    private void handleTp(CommandSender sender, String[] args) {
        var msg = plugin.messages();
        if (!sender.hasPermission("ultras.homes.admin.tp") && !sender.hasPermission("ultras.homes.admin")) {
            msg.send(sender, "general.no-permission");
            return;
        }
        if (!(sender instanceof Player admin)) {
            msg.send(sender, "general.player-only");
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "admin.target-required");
            return;
        }
        String targetName = args[1];
        withTarget(sender, targetName, (uuid, name) -> plugin.homes().withData(uuid, name, data -> {
            if (args.length >= 3) {
                var entry = HomeLookup.byName(data, args[2]);
                if (entry == null) {
                    msg.send(admin, "admin.tp-not-found", Text.map("target", name, "home", args[2]));
                    return;
                }
                Home h = entry.getValue();
                if (plugin.cfg().worldBlacklisted(h.world()) && !admin.hasPermission("ultras.homes.bypass.world")) {
                    msg.send(admin, "teleport.world-blocked");
                    return;
                }
                Location dest = LocationUtil.build(h.world(), h.x(), h.y(), h.z(), h.yaw(), h.pitch());
                if (dest == null) {
                    msg.send(admin, "teleport.invalid-location");
                    return;
                }
                if (plugin.cfg().logAdminActions()) {
                    plugin.getLogger().info("[ADMIN_TELEPORT] " + admin.getName() + " -> " + name + ":" + h.name());
                }
                plugin.teleport().start(admin, dest, name + ": " + h.name(), () -> LocationUtil.isValid(h.world(), h.x(), h.y(), h.z()));
            } else {
                plugin.gui().openPlayerHomes(admin, uuid, data, 0);
            }
        }));
    }

    // ------------------------------------------------------------------ home <player>  (admin GUI of a player's homes)
    private void handleHome(CommandSender sender, String[] args) {
        var msg = plugin.messages();
        if (!allowed(sender, "home")) {
            msg.send(sender, "general.no-permission");
            return;
        }
        if (!(sender instanceof Player admin)) {
            msg.send(sender, "general.player-only");
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "general.invalid-usage", Text.map("usage", "/home admin home [player]"));
            return;
        }
        withTarget(sender, args[1], (uuid, name) -> plugin.homes().withData(uuid, name, data -> {
            if (plugin.cfg().logAdminActions()) {
                plugin.getLogger().info("[ADMIN_HOMES_GUI] " + admin.getName() + " opened the homes of " + name);
            }
            plugin.gui().openPlayerHomes(admin, uuid, data, 0);
        }));
    }

    // ------------------------------------------------------------------ setting <player>  (admin edits a player's settings)
    private void handleSetting(CommandSender sender, String[] args) {
        var msg = plugin.messages();
        if (!allowed(sender, "setting")) {
            msg.send(sender, "general.no-permission");
            return;
        }
        if (!(sender instanceof Player admin)) {
            msg.send(sender, "general.player-only");
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "general.invalid-usage", Text.map("usage", "/home admin setting [player]"));
            return;
        }
        withTarget(sender, args[1], (uuid, name) -> plugin.homes().withData(uuid, name, data ->
                plugin.gui().openSettings(admin, data, () -> plugin.gui().openPlayerHomes(admin, uuid, data, 0))));
    }

    private static boolean allowed(CommandSender sender, String sub) {
        return sender.hasPermission("ultras.homes.admin") || sender.hasPermission("ultras.homes.admin." + sub);
    }

    /** True when the sender may use at least one admin subcommand (used to show/hide "admin" and the admin command in Tab). */
    public static boolean hasAnyAdminPermission(CommandSender sender) {
        if (sender.hasPermission("ultras.homes.admin")) return true;
        for (String s : SUB) {
            if (sender.hasPermission("ultras.homes.admin." + s)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ reload
    private void handleReload(CommandSender sender) {
        var msg = plugin.messages();
        if (!sender.hasPermission("ultras.homes.admin.reload") && !sender.hasPermission("ultras.homes.admin")) {
            msg.send(sender, "general.no-permission");
            return;
        }
        plugin.reloadAll();
        msg.send(sender, "general.reload-done");
    }

    // ------------------------------------------------------------------ helpers
    private interface TargetCallback {
        void accept(UUID uuid, String name);
    }

    private void withTarget(CommandSender sender, String nameOrUuid, TargetCallback cb) {
        Player online = Bukkit.getPlayerExact(nameOrUuid);
        if (online != null) {
            cb.accept(online.getUniqueId(), online.getName());
            return;
        }
        UUID byUuid = tryUuid(nameOrUuid);
        if (byUuid != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(byUuid);
            cb.accept(byUuid, op.getName() != null ? op.getName() : nameOrUuid);
            return;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(nameOrUuid);
        if (op.hasPlayedBefore() || plugin.homes().storage().exists(op.getUniqueId())) {
            cb.accept(op.getUniqueId(), nameOrUuid);
            return;
        }
        plugin.messages().send(sender, "general.unknown-player", Text.map("player", nameOrUuid));
    }

    private static UUID tryUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Player offlineOrOnline(UUID uuid) {
        return Bukkit.getPlayer(uuid);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!hasAnyAdminPermission(sender)) return out;
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String s : SUB) {
                if (allowed(sender, s) && s.startsWith(typed)) out.add(s);
            }
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUB.contains(sub) && !sub.equals("reset") && !sub.equals("settings") && !sub.equals("homes")) return out;
        if (args.length == 2 && List.of("add", "set", "rest", "reset", "tp", "home", "homes", "setting", "settings").contains(sub)) {
            if (List.of("add", "set", "rest", "reset").contains(sub) && "all".startsWith(typed)) out.add("all");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(typed)) out.add(p.getName());
            }
            return out;
        }
        if (args.length == 3 && (sub.equals("add") || sub.equals("set"))) {
            for (String n : List.of("1", "2", "5", "10")) {
                if (n.startsWith(typed)) out.add(n);
            }
            return out;
        }
        if (args.length == 3 && sub.equals("tp")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                PlayerHomeData cached = plugin.homes().peek(target.getUniqueId());
                if (cached != null) {
                    for (Home h : cached.homes.values()) {
                        if (h.name().toLowerCase(Locale.ROOT).startsWith(typed)) out.add(h.name());
                    }
                }
            }
        }
        return out;
    }
}
