package com.donut.module.settings;

public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final Class<E> enumClass;

    public EnumSetting(String name, E defaultValue, String description) {
        super(name, defaultValue, description);
        this.enumClass = defaultValue.getDeclaringClass();
    }

    public E[] values() {
        return enumClass.getEnumConstants();
    }

    public void cycle(int direction) {
        E[] vals = values();
        int next = (get().ordinal() + direction + vals.length) % vals.length;
        set(vals[next]);
    }

    @Override
    public String serialize() {
        return get().name();
    }

    @Override
    public void deserialize(String raw) {
        for (E v : values()) {
            if (v.name().equalsIgnoreCase(raw.trim())) {
                set(v);
                return;
            }
        }
    }
}
