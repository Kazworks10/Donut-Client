package com.donut.module.settings;

import java.util.Locale;

/** Double-backed number setting with slider bounds and display decimals. */
public final class NumberSetting extends Setting<Double> {
    public final double min;
    public final double max;
    public final double step;
    public final int decimals;

    public NumberSetting(String name, double defaultValue, double min, double max, double step, int decimals, String description) {
        super(name, defaultValue, description);
        this.min = min;
        this.max = max;
        this.step = step;
        this.decimals = decimals;
    }

    public int intValue() {
        return (int) Math.round(get());
    }

    public float floatValue() {
        return get().floatValue();
    }

    @Override
    public void set(Double newValue) {
        super.set(Math.clamp(newValue, min, max));
    }

    @Override
    public String serialize() {
        return String.format(Locale.ROOT, "%." + decimals + "f", get());
    }

    @Override
    public void deserialize(String raw) {
        try {
            set(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException ignored) {
        }
    }
}
