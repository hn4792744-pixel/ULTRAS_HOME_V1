package me.uc.hussein.ultrashomes.listener;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Cancels a pending countdown on unsafe movement/damage/death, and blocks damage during arrival protection. */
public final class TeleportListener implements Listener {
    private final UltrasHomesPlugin plugin;

    public TeleportListener(UltrasHomesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.cfg().cancelOnMove()) return;
        Player p = e.getPlayer();
        if (!plugin.teleport().hasPending(p.getUniqueId())) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (plugin.teleport().movedFromStart(p, to)) {
            plugin.teleport().cancel(p.getUniqueId(), "cancelled-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOtherTeleport(PlayerTeleportEvent e) {
        // any other plugin/vanilla teleport (ender pearl, command, portal...) also invalidates the countdown
        plugin.teleport().cancel(e.getPlayer().getUniqueId(), "cancelled-other");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        plugin.teleport().cancel(e.getEntity().getUniqueId(), "cancelled-death");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (plugin.teleport().hasPending(p.getUniqueId())) {
            plugin.teleport().cancel(p.getUniqueId(), "cancelled-damage");
        }
        if (plugin.teleport().protectionManager().isProtected(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }
}
