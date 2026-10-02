package me.uc.hussein.ultrashomes.teleport;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

/** State of one in-progress teleport countdown for a player. */
final class PendingTeleport {
    final Location startLocation;
    final Runnable onComplete;
    BukkitTask task;
    int secondsLeft;

    PendingTeleport(Location startLocation, int secondsLeft, Runnable onComplete) {
        this.startLocation = startLocation;
        this.secondsLeft = secondsLeft;
        this.onComplete = onComplete;
    }
}
