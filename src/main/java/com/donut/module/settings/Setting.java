package com.donut.module.settings;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Base setting: typed value, default, optional visibility predicate and change callback.
 * Serialization is handled by concrete subclasses (all values map to TOML-friendly scalars).
 */
public abstract class Setting<T> {
    public final String name;
    public final String description;
    private final T defaultValue;
    private T value;
    private BooleanSupplier visible = () -> true;
    private Consumer<T> onChanged;

    protected Setting(String name, T defaultValue, String description) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.description = description;
    }

    public T get() {
        return value;
    }

    public void set(T newValue) {
        this.value = newValue;
        if (onChanged != null) onChanged.accept(newValue);
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public void reset() {
        set(defaultValue);
    }

    public boolean isVisible() {
        return visible.getAsBoolean();
    }

    public Setting<T> visibleWhen(BooleanSupplier predicate) {
        this.visible = predicate;
        return this;
    }

    public Setting<T> onChanged(Consumer<T> callback) {
        this.onChanged = callback;
        return this;
    }

    /** TOML-friendly scalar form of the current value. */
    public abstract String serialize();

    /** Restores the value from its serialized form; ignores malformed input. */
    public abstract void deserialize(String raw);
}
