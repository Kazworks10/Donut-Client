package com.donut.module.modules;

import java.util.Locale;

/**
 * Pure AutoSell decision helpers: identifying the server's sell button by its
 * name/lore marker text, and normalizing the configured item id. Minecraft-free
 * so both are testable headless.
 */
public final class SellButtons {
    /** Marker text the Donut SMP sell button carries in its name or lore. */
    public static final String MARKER = "click to sell items";

    private SellButtons() {
    }

    /** True when the combined name+lore label of the slot-53 stack is the sell button. */
    public static boolean isSellButton(String combinedNameAndLore, boolean isFurnaceToken, boolean isEmpty) {
        if (isEmpty || isFurnaceToken) return false;
        return normalized(combinedNameAndLore).contains(MARKER);
    }

    /** Lowercased, trimmed label as the matcher sees it. */
    public static String normalized(String label) {
        return label == null ? "" : label.strip().toLowerCase(Locale.ROOT);
    }

    /** Normalized item id from a config string: trimmed, lowercased, minecraft: prefix removed. */
    public static String normalizeItemId(String raw) {
        String t = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("minecraft:") ? t.substring("minecraft:".length()) : t;
    }
}
