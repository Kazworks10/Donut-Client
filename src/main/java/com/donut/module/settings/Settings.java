package com.donut.module.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ordered container of a module's settings with lookup by name.
 */
public final class Settings implements Iterable<Setting<?>> {
    private final List<Setting<?>> all = new ArrayList<>();
    private final Map<String, Setting<?>> byName = new LinkedHashMap<>();

    public void add(Setting<?> setting) {
        all.add(setting);
        byName.put(setting.name, setting);
    }

    public Setting<?> get(String name) {
        return byName.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T extends Setting<?>> T get(String name, Class<T> type) {
        return (T) byName.get(name);
    }

    public List<Setting<?>> all() {
        return List.copyOf(all);
    }

    public int size() {
        return all.size();
    }

    @Override
    public java.util.Iterator<Setting<?>> iterator() {
        return all.iterator();
    }
}
