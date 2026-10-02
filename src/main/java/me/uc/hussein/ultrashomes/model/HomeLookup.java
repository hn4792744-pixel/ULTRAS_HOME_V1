package me.uc.hussein.ultrashomes.model;

import java.util.Map;

/** Small helper shared by commands and GUIs to find a home by its display name. */
public final class HomeLookup {
    private HomeLookup() {
    }

    public static Map.Entry<Integer, Home> byName(PlayerHomeData data, String name) {
        if (name == null) return null;
        for (Map.Entry<Integer, Home> e : data.homes.entrySet()) {
            if (e.getValue().name().equalsIgnoreCase(name)) return e;
        }
        return null;
    }
}
