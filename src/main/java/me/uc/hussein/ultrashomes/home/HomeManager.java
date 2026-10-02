package me.uc.hussein.ultrashomes.home;

import me.uc.hussein.ultrashomes.UltrasHomesPlugin;
import me.uc.hussein.ultrashomes.model.Home;
import me.uc.hussein.ultrashomes.model.PlayerHomeData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Lazy-loading cache of player home data. Nothing is read from disk at startup: a player's file is
 * only loaded the first time it is actually needed (join, command, admin lookup), read on an async
 * thread, then handed back to the main thread. Data is unloaded from memory some time after the
 * player leaves (storage.unload-delay-seconds) so a quick re-join or admin command right after
 * doesn't force a re-read from disk.
 */
public final class HomeManager {
    private final UltrasHomesPlugin plugin;
    private final HomeStorage storage;
    private final Map<UUID, PlayerHomeData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> loading = new ConcurrentHashMap<>();
    private final Map<UUID, List<Consumer<PlayerHomeData>>> waiters = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> unloadTasks = new HashMap<>();
    private BukkitTask saveTask;

    public HomeManager(UltrasHomesPlugin plugin) {
        this.plugin = plugin;
        this.storage = new HomeStorage(plugin);
    }

    public void start() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        long ticks = plugin.cfg().saveIntervalTicks();
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> flushDirty(false), ticks, ticks);
    }

    public void stop() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        for (BukkitTask t : unloadTasks.values()) {
            t.cancel();
        }
        unloadTasks.clear();
        flushDirty(true);
    }

    /** Only returns data already in memory - never triggers a load. Safe to call from anywhere. */
    public PlayerHomeData peek(UUID uuid) {
        return cache.get(uuid);
    }

    /** Loads (if needed) and hands the data back on the main thread. Cancels any pending unload. */
    public void withData(UUID uuid, String fallbackName, Consumer<PlayerHomeData> callback) {
        cancelPendingUnload(uuid);
        PlayerHomeData cached = cache.get(uuid);
        if (cached != null) {
            cached.lastTouched = System.currentTimeMillis();
            callback.accept(cached);
            return;
        }
        waiters.computeIfAbsent(uuid, k -> new ArrayList<>()).add(callback);
        if (loading.putIfAbsent(uuid, Boolean.TRUE) != null) {
            return; // a load is already in flight, the waiter list above will be notified
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerHomeData loaded = storage.load(uuid, fallbackName);
            if (plugin.cfg().logData()) {
                plugin.getLogger().info("[data] Loaded homes for " + uuid + " (" + loaded.homes.size() + " homes).");
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerHomeData existing = cache.putIfAbsent(uuid, loaded);
                PlayerHomeData use = existing != null ? existing : loaded;
                use.lastTouched = System.currentTimeMillis();
                loading.remove(uuid);
                List<Consumer<PlayerHomeData>> pending = waiters.remove(uuid);
                if (pending != null) {
                    for (Consumer<PlayerHomeData> c : pending) {
                        c.accept(use);
                    }
                }
            });
        });
    }

    public void markDirty(PlayerHomeData data) {
        data.dirty = true;
        data.lastTouched = System.currentTimeMillis();
    }

    /** Called on player quit: save immediately, then schedule the in-memory copy for removal. */
    public void onQuit(Player p) {
        afterOffline(p.getUniqueId());
    }

    /**
     * Saves (if dirty) and schedules removal from memory of a player's data once they are offline.
     * Also used when a player disconnected while their data was still loading on join.
     */
    public void afterOffline(UUID id) {
        PlayerHomeData d = cache.get(id);
        if (d == null) return;
        if (d.dirty) {
            saveOne(d);
        }
        cancelPendingUnload(id);
        long delay = plugin.cfg().unloadDelayTicks();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            unloadTasks.remove(id);
            PlayerHomeData current = cache.get(id);
            if (current != null && !current.dirty && Bukkit.getPlayer(id) == null) {
                cache.remove(id);
            }
        }, delay);
        unloadTasks.put(id, task);
    }

    private void cancelPendingUnload(UUID id) {
        BukkitTask t = unloadTasks.remove(id);
        if (t != null) t.cancel();
    }

    private void saveOne(PlayerHomeData d) {
        HomeStorage.Snapshot snap = HomeStorage.snapshot(d);
        d.dirty = false;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            storage.save(snap);
            if (plugin.cfg().logData()) {
                plugin.getLogger().info("[data] Saved homes for " + snap.uuid() + ".");
            }
        });
    }

    public void flushDirty(boolean sync) {
        List<PlayerHomeData> dirty = new ArrayList<>();
        for (PlayerHomeData d : cache.values()) {
            if (d.dirty) dirty.add(d);
        }
        if (dirty.isEmpty()) return;
        List<HomeStorage.Snapshot> snaps = new ArrayList<>(dirty.size());
        for (PlayerHomeData d : dirty) {
            snaps.add(HomeStorage.snapshot(d));
            d.dirty = false;
        }
        Runnable job = () -> {
            for (HomeStorage.Snapshot s : snaps) {
                storage.save(s);
            }
        };
        if (sync) {
            job.run();
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, job);
        }
    }

    // ------------------------------------------------------------------ mutation helpers (main thread only)
    public int nextFreeSlot(PlayerHomeData data, int limit) {
        for (int i = 1; i <= limit; i++) {
            if (!data.homes.containsKey(i)) return i;
        }
        return -1;
    }

    public void setHome(PlayerHomeData data, int number, Home home) {
        data.homes.put(number, home);
        markDirty(data);
    }

    public void removeHome(PlayerHomeData data, int number) {
        data.homes.remove(number);
        markDirty(data);
    }

    public void clearHomes(PlayerHomeData data) {
        data.homes.clear();
        markDirty(data);
    }

    /** Deletes any home whose slot number is above the given limit. Returns how many were removed. */
    public int deleteAboveLimit(PlayerHomeData data, int limit) {
        int removed = 0;
        var it = data.homes.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getKey() > limit) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) markDirty(data);
        return removed;
    }

    // ------------------------------------------------------------------ bulk (admin "all") operations
    /**
     * Applies {@code mutator} to every UUID, one at a time, without ever holding more than one
     * *uncached* player's data in memory at once (spec #42: no full "load everyone" for /home_admin ... all).
     * Must be called from an async thread - it performs blocking disk IO for players not already cached.
     */
    public int bulkTransform(java.util.List<UUID> uuids, java.util.function.Consumer<PlayerHomeData> mutator) {
        int count = 0;
        for (UUID id : uuids) {
            PlayerHomeData cached = cache.get(id);
            if (cached != null) {
                mutator.accept(cached);
                cached.dirty = true;
                count++;
            } else {
                PlayerHomeData temp = storage.load(id, null);
                mutator.accept(temp);
                storage.save(HomeStorage.snapshot(temp));
                count++;
            }
        }
        return count;
    }

    public HomeStorage storage() {
        return storage;
    }

    /** Every UUID that has ever saved a home, for "all" admin operations. */
    public List<UUID> everyKnownPlayer() {
        Set<UUID> out = new java.util.LinkedHashSet<>(storage.allStoredUuids());
        out.addAll(cache.keySet());
        return new ArrayList<>(out);
    }
}
