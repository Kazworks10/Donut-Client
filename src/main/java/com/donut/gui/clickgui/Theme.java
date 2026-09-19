package com.donut.gui.clickgui;

/**
 * Central GUI palette and metrics. Dark purple "donut" theme by default;
 * all colors are ARGB.
 */
public final class Theme {
    private Theme() {
    }

    // Palette
    public static final int SCRIM = 0x88000000;
    public static final int PANEL = 0xE6161226;
    public static final int HEADER = 0xF0221842;
    public static final int OUTLINE = 0xFF3A2E66;
    public static final int TEXT = 0xFFEAE6FF;
    public static final int TEXT_DIM = 0xFF9A8FC0;
    public static final int ACCENT = 0xFFB478FF;
    public static final int ACCENT_DIM = 0xFF6C4BB8;
    public static final int ENABLED = 0xFF7CE38B;
    public static final int DISABLED = 0xFF55506E;
    public static final int ROW = 0x00FFFFFF;          // transparent
    public static final int ROW_HOVER = 0x22FFFFFF;
    public static final int ROW_EXPANDED = 0x14B478FF;
    public static final int SLIDER_BG = 0xFF2A2347;
    public static final int SLIDER_FILL = 0xFF8A5CD8;
    public static final int INPUT_BG = 0xFF221A3E;
    public static final int DANGER = 0xFFE15A5A;

    // Metrics (px, gui-scaled)
    public static final int PANEL_W = 124;
    public static final int HEADER_H = 16;
    public static final int ROW_H = 14;
    public static final int MAX_CONTENT_H = 190;
    public static final int PAD = 4;
}
