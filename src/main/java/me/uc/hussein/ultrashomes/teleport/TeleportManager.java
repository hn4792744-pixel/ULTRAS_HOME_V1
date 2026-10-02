package me.uc.hussein.ultrashomes.teleport;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import me.uc.hussein.ultrashomes.util.Text;
import me.uc.hussein.ultrashomes.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs teleport countdowns, movement/damage/death cancellation, post-teleport cooldown and arrival
 * protection. Every Bukkit API call happens on the main thread (BukkitRunnable ticking every second).
 */
public final class TeleportManager {
    private final UltrasHomesPlugin plugin;
    private final ProtectionManager protection = new ProtectionManager();
    private final Map<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();

    public TeleportManager(UltrasHomesPlugin plugin) {
        this.plugin = plugin;
    }

    public ProtectionManager protectionManager() {
        return protection;
    }

    public boolean hasPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public long cooldownRemainingMs(Player p) {
        if (p.hasPermission("ultras.homes.bypass.cooldown") && plugin.cfg().adminBypassCooldown()) return 0;
        Long until = cooldownUntil.get(p.getUniqueId());
        if (until == null) return 0;
        long remaining = until - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * Starts a countdown teleport to {@code destination}. {@code homeLabel} is the already-formatted
     * display name used in messages (works both for the player's own home and an admin's label like
     * "PlayerName: home1"). {@code destinationValidator} is re-checked right before the actual
     * teleport in case the world was unloaded meanwhile.
     */
    public void start(Player p, Location destination, String homeLabel, java.util.function.BooleanSupplier destinationValidator) {
        UUID id = p.getUniqueId();
        var msg = plugin.messages();
        if (pending.containsKey(id)) {
            msg.send(p, "teleport.already-pending");
            return;
        }
        long remaining = cooldownRemainingMs(p);
        if (remaining > 0) {
            msg.send(p, "teleport.cooldown", Text.map("remaining", TimeUtil.humanize(remaining)));
            return;
        }
        int delay = plugin.cfg().teleportDelay();
        Map<String, String> ph = Text.map("home", homeLabel, "delay", String.valueOf(delay));
        msg.send(p, "teleport.starting", ph);

        if (delay <= 0) {
            complete(p, destination, homeLabel, destinationValidator);
            return;
        }

        PendingTeleport pt = new PendingTeleport(p.getLocation().clone(), delay,
                () -> complete(p, destination, homeLabel, destinationValidator));
        pending.put(id, pt);
        tickCountdown(p, pt);
    }

    private void tickCountdown(Player p, PendingTeleport pt) {
        UUID id = p.getUniqueId();
        pt.task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                PendingTeleport current = pending.get(id);
                if (current != pt) {
                    return; // was cancelled/replaced
                }
                if (!p.isOnline()) {
                    cancelInternal(id, null);
                    return;
                }
                if (pt.secondsLeft <= 0) {
                    pending.remove(id);
                    pt.task.cancel();
                    pt.onComplete.run();
                    return;
                }
                int showFrom = plugin.cfg().actionBarShowFrom();
                if (plugin.cfg().actionBarEnabled() && (showFrom <= 0 || pt.secondsLeft <= showFrom)) {
                    Component c = plugin.messages().c(p, "teleport.countdown", Text.map("seconds", String.valueOf(pt.secondsLeft)));
                    p.sendActionBar(c);
                }
                if (plugin.cfg().get().getBoolean("teleport.sounds.enabled", true)) {
                    plugin.settings().play(p, plugin.cfg().get().getString("teleport.sounds.countdown", ""), 0.6f, 1.4f);
                }
                pt.secondsLeft--;
            }
        }, 0L, 20L);
    }

    private void complete(Player p, Location destination, String homeLabel, java.util.function.BooleanSupplier destinationValidator) {
        var msg = plugin.messages();
        if (!p.isOnline()) return;
        if (!destinationValidator.getAsBoolean()) {
            msg.send(p, "teleport.invalid-location");
            playSound(p, "error");
            return;
        }
        p.closeInventory();
        p.teleportAsync(destination).thenAccept(success -> {
            if (!p.isOnline()) return;
            if (!Boolean.TRUE.equals(success)) {
                msg.send(p, "teleport.invalid-location");
                playSound(p, "error");
                return;
            }
            int cooldown = plugin.cfg().teleportCooldown();
            if (cooldown > 0) {
                cooldownUntil.put(p.getUniqueId(), System.currentTimeMillis() + cooldown * 1000L);
            }
            if (plugin.cfg().protectionEnabled() && plugin.cfg().protectionDuration() > 0) {
                protection.protect(p.getUniqueId(), plugin.cfg().protectionDuration() * 1000L);
            }
            msg.send(p, "teleport.success", Text.map("home", homeLabel));
            playSound(p, "success");
        });
    }

    private void playSound(Player p, String key) {
        if (!plugin.cfg().get().getBoolean("teleport.sounds.enabled", true)) return;
        plugin.settings().play(p, plugin.cfg().get().getString("teleport.sounds." + key, ""), 1f, 1f);
    }

    /** Cancels a pending teleport for a reason (move/damage/death/quit/disable/other) and messages the player if online. */
    public void cancel(UUID uuid, String reasonKey) {
        cancelInternal(uuid, reasonKey);
    }

    private void cancelInternal(UUID uuid, String reasonKey) {
        PendingTeleport pt = pending.remove(uuid);
        if (pt == null) return;
        if (pt.task != null) pt.task.cancel();
        if (reasonKey == null) return;
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            plugin.messages().send(p, "teleport." + reasonKey);
            playSound(p, "cancelled");
        }
    }

    /** True if the player is further than move-tolerance from the exact spot where their countdown started. */
    public boolean movedFromStart(Player p, Location to) {
        PendingTeleport pt = pending.get(p.getUniqueId());
        if (pt == null || to == null) return false;
        Location start = pt.startLocation;
        if (start.getWorld() == null || to.getWorld() == null || !start.getWorld().equals(to.getWorld())) return true;
        return start.distanceSquared(to) > (plugin.cfg().moveTolerance() * plugin.cfg().moveTolerance());
    }

    /** True if the player moved beyond the configured tolerance between two positions (kept for API compatibility). */
    public boolean movedTooFar(Player p, Location from, Location to) {
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) return true;
        return from.distanceSquared(to) > (plugin.cfg().moveTolerance() * plugin.cfg().moveTolerance());
    }

    public void shutdown() {
        for (UUID id : new java.util.ArrayList<>(pending.keySet())) {
            cancelInternal(id, null);
        }
        protection.clearAll();
        cooldownUntil.clear();
    }
}
