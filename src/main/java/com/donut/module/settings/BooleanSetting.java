package com.donut.module.settings;

public final class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, boolean defaultValue, String description) {
        super(name, defaultValue, description);
    }

    public void toggle() {
        set(!get());
    }

    @Override
    public String serialize() {
        return Boolean.toString(get());
    }

    @Override
    public void deserialize(String raw) {
        if (raw.equalsIgnoreCase("true") || raw.equals("1")) set(true);
        else if (raw.equalsIgnoreCase("false") || raw.equals("0")) set(false);
    }
}
