package com.donut.module.settings;

public final class ColorSetting extends Setting<Integer> {
    public ColorSetting(String name, int argb, String description) {
        super(name, argb, description);
    }

    public int red() {
        return (get() >>> 16) & 0xFF;
    }

    public int green() {
        return (get() >>> 8) & 0xFF;
    }

    public int blue() {
        return get() & 0xFF;
    }

    public int alpha() {
        return (get() >>> 24) & 0xFF;
    }

    /** Sets from RGB, preserving the alpha channel. */
    public void setRgb(int r, int g, int b) {
        set((get() & 0xFF000000) | (r << 16) | (g << 8) | b);
    }

    @Override
    public String serialize() {
        return String.format("#%08X", get());
    }

    @Override
    public void deserialize(String raw) {
        String s = raw.trim().replace("#", "");
        try {
            set(s.length() == 6 ? 0xFF000000 | (int) Long.parseLong(s, 16) : (int) Long.parseLong(s, 16));
        } catch (NumberFormatException ignored) {
        }
    }
}
