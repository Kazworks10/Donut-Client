package com.donut.module.settings;

public final class StringSetting extends Setting<String> {
    public StringSetting(String name, String defaultValue, String description) {
        super(name, defaultValue, description);
    }

    @Override
    public String serialize() {
        return get();
    }

    @Override
    public void deserialize(String raw) {
        set(raw);
    }
}
