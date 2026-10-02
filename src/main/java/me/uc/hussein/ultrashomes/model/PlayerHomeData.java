package me.uc.hussein.ultrashomes.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory representation of one player's home file. Kept in HomeManager's cache while the
 * player is online or otherwise needed, and released after being saved.
 */
public final class PlayerHomeData {
    public final UUID uuid;
    public String name;
    /** null = use the permission/default-based limit; non-null = admin override (already capped to maximum). */
    public Integer limitOverride;
    /** home number -> Home, insertion order preserved for stable listing. */
    public final Map<Integer, Home> homes = new LinkedHashMap<>();
    /** Personal setting: plugin chat messages for this player (default ON). Persisted in the player's file. */
    public volatile boolean chatMessages = true;
    /** Personal setting: ULTRAS_HOMES sounds for this player (default ON). Persisted in the player's file. */
    public volatile boolean pluginSounds = true;
    /** Personal language ("en" / "ar"); null = use language.default from config.yml. */
    public volatile String language;
    public volatile boolean dirty;
    public volatile long lastTouched = System.currentTimeMillis();

    public PlayerHomeData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "Unknown" : name;
    }
}
