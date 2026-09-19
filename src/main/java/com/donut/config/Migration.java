package com.donut.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Config version migrations. Each step upgrades a raw config map in place.
 * Migrations must never lose user settings (plan hard constraint).
 */
public final class Migration {
    /** Current schema version written by this build. */
    public static final int CURRENT = 2;

    private Migration() {
    }

    /** Applies all needed migrations to reach {@link #CURRENT}. Returns the (possibly new) version. */
    @SuppressWarnings("unchecked")
    public static int migrate(Map<String, Object> root) {
        int version = intOf(root.get("version"), 1);
        while (version < CURRENT) {
            switch (version) {
                case 1 -> v1ToV2(root);
                default -> {
                    // Unknown future version: leave as-is; unknown keys are preserved on save.
                }
            }
            version = intOf(root.get("version"), version + 1);
        }
        root.putIfAbsent("version", CURRENT);
        return CURRENT;
    }

    /**
     * v1 -> v2: [gui] open keybind moved from a symbolic name string ("RSHIFT")
     * to numeric { key, mods } pair.
     */
    @SuppressWarnings("unchecked")
    static void v1ToV2(Map<String, Object> root) {
        Object guiObj = root.get("gui");
        if (guiObj instanceof Map<?, ?> guiRaw) {
            Map<String, Object> gui = new LinkedHashMap<>((Map<String, Object>) guiRaw);
            Object key = gui.remove("open_key");
            if (key instanceof String s) {
                int code = GLFW_NAME_CODES.getOrDefault(s.trim().toUpperCase(), 344); // fallback RSHIFT
                gui.put("key", code);
                gui.putIfAbsent("mods", 1); // shift, matching the old RSHIFT default
            }
            gui.putIfAbsent("key", 344);
            gui.putIfAbsent("mods", 1);
            root.put("gui", gui);
        }
        root.put("version", 2);
    }

    private static int intOf(Object o, int fallback) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    /** Minimal symbolic-name table used only by the v1 migration. */
    static final Map<String, Integer> GLFW_NAME_CODES = Map.ofEntries(
            Map.entry("RSHIFT", 344),
            Map.entry("LSHIFT", 340),
            Map.entry("RCTRL", 345),
            Map.entry("LCTRL", 341),
            Map.entry("RALT", 346),
            Map.entry("LALT", 342),
            Map.entry("SPACE", 32),
            Map.entry("TAB", 258),
            Map.entry("BACKSLASH", 92),
            Map.entry("GRAVE", 96)
    );
}
