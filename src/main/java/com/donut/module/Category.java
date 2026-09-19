package com.donut.module;

/**
 * Module categories for GUI grouping and ordering.
 */
public enum Category {
    COMBAT("Combat", 0xFFE15A5A),
    MOVEMENT("Movement", 0xFF5AE187),
    RENDER("Render", 0xFF5AB0E1),
    PLAYER("Player", 0xFFB78AE0),
    WORLD("World", 0xFFE0B25A),
    MISC("Misc", 0xFF9AA3B2);

    public final String displayName;
    public final int accentColor;

    Category(String displayName, int accentColor) {
        this.displayName = displayName;
        this.accentColor = accentColor;
    }
}
