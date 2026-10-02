package me.uc.hussein.ultrashomes.command;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /sethome and /homes both just open the Homes GUI - saving only ever happens when the player
 * clicks a dye inside it (spec #14/#55: no location is captured just by running the command).
 */
public final class SetHomeCommand implements CommandExecutor {
    private final UltrasHomesPlugin plugin;
    private final String permission;

    public SetHomeCommand(UltrasHomesPlugin plugin, String permission) {
        this.plugin = plugin;
        this.permission = permission;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msg = plugin.messages();
        if (!(sender instanceof Player p)) {
            msg.send(sender, "general.player-only");
            return true;
        }
        if (!p.hasPermission(permission) && !p.hasPermission("ultras.homes.use")) {
            msg.send(p, "general.no-permission");
            return true;
        }
        if (plugin.cfg().worldBlacklisted(p.getWorld().getName())) {
            msg.send(p, "general.world-disabled");
            return true;
        }
        plugin.homes().withData(p.getUniqueId(), p.getName(), data -> plugin.gui().openHomes(p, data, 0));
        return true;
    }
}
