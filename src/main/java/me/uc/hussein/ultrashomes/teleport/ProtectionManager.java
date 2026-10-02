package me.uc.hussein.ultrashomes.teleport;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks temporary post-teleport damage immunity. Checked by TeleportListener on EntityDamageEvent. */
public final class ProtectionManager {
    private final Map<UUID, Long> until = new ConcurrentHashMap<>();

    public void protect(UUID uuid, long durationMs) {
        if (durationMs <= 0) {
            until.remove(uuid);
            return;
        }
        until.put(uuid, System.currentTimeMillis() + durationMs);
    }

    public boolean isProtected(UUID uuid) {
        Long t = until.get(uuid);
        if (t == null) return false;
        if (System.currentTimeMillis() >= t) {
            until.remove(uuid);
            return false;
        }
        return true;
    }

    public void clear(UUID uuid) {
        until.remove(uuid);
    }

    public void clearAll() {
        until.clear();
    }
}
