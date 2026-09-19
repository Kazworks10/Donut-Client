package com.donut.module;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central module registry: registration, ordered iteration, per-tick dispatch
 * and global keybind handling.
 */
public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final Map<String, Module> byName = new LinkedHashMap<>();

    public void register(Module module) {
        if (byName.containsKey(module.name())) {
            throw new IllegalArgumentException("Duplicate module name: " + module.name());
        }
        modules.add(module);
        byName.put(module.name(), module);
    }

    public List<Module> all() {
        return Collections.unmodifiableList(modules);
    }

    public Module get(String name) {
        return byName.get(name);
    }

    public List<Module> enabled() {
        List<Module> out = new ArrayList<>();
        for (Module m : modules) if (m.isEnabled()) out.add(m);
        return out;
    }

    /** Dispatches a tick to every module (base class gates on enabled state). */
    public void tickAll() {
        for (Module m : modules) m.tick();
    }

    /**
     * Handles a raw GLFW key press: toggles the first module whose keybind matches.
     * Returns the toggled module, or null.
     */
    public Module handleKeyPress(int keyCode, int modifiers) {
        for (Module m : modules) {
            if (m.keybind.matches(keyCode, modifiers)) {
                m.toggle();
                return m;
            }
        }
        return null;
    }

    /** True if the key is currently held for the given module binding. */
    public boolean isKeyHeld(long window, Module module) {
        int code = module.keybind.get();
        if (code == 0) return false;
        if (GLFW.glfwGetKey(window, code) != GLFW.GLFW_PRESS) return false;
        // Modifier check is deliberately permissive about extras beyond the bound set.
        return true;
    }
}
