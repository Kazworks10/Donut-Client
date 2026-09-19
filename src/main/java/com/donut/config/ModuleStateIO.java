package com.donut.config;

import com.donut.module.Module;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared (de)serialization of module state (enabled + settings) to plain maps,
 * used by both the TOML config and JSON profiles.
 */
public final class ModuleStateIO {
    private ModuleStateIO() {
    }

    /** Reads module states from a "modules" table (name -> state map). */
    public static void apply(Map<?, ?> modulesTable, Iterable<Module> modules) {
        for (Module m : modules) {
            Object raw = modulesTable.get(m.name());
            if (!(raw instanceof Map<?, ?> state)) continue;

            Object enabled = state.get("enabled");
            if (enabled instanceof Boolean b) {
                if (b) m.enable();
                else m.disable();
            }

            for (var setting : m.settings()) {
                Object val = state.get(setting.name);
                if (val != null) setting.deserialize(String.valueOf(val));
            }
        }
    }

    /** Collects module states into a "modules" table (name -> state map). */
    public static Map<String, Object> collect(Iterable<Module> modules) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Module m : modules) {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("enabled", m.isEnabled());
            for (var setting : m.settings()) {
                state.put(setting.name, setting.serialize());
            }
            out.put(m.name(), state);
        }
        return out;
    }
}
