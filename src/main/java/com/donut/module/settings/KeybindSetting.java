package com.donut.module.settings;

import org.lwjgl.glfw.GLFW;

/**
 * Raw GLFW keybind with modifier mask (SHIFT=1, CTRL=2, ALT=4). Keycode 0 = unbound.
 * The GUI sets {@link #listening} while waiting for a key capture.
 */
public final class KeybindSetting extends Setting<Integer> {
    public int modifiers;
    public boolean listening;

    public KeybindSetting(String name, int keyCode, int modifiers, String description) {
        super(name, keyCode, description);
        this.modifiers = modifiers;
    }

    public boolean matches(int keyCode, int mods) {
        return keyCode != 0 && keyCode == get()
                && (modifiers & (1 | 2 | 4)) == (mods & (1 | 2 | 4));
    }

    public String display() {
        if (get() == 0) return "NONE";
        StringBuilder sb = new StringBuilder();
        if ((modifiers & 1) != 0) sb.append("Shift+");
        if ((modifiers & 2) != 0) sb.append("Ctrl+");
        if ((modifiers & 4) != 0) sb.append("Alt+");
        String name = GLFW.glfwGetKeyName(get(), 0);
        sb.append(name == null ? "Key" + get() : name.toUpperCase());
        return sb.toString();
    }

    @Override
    public String serialize() {
        return get() + (modifiers != 0 ? ":" + modifiers : "");
    }

    @Override
    public void deserialize(String raw) {
        String[] parts = raw.split(":");
        try {
            set(Integer.parseInt(parts[0].trim()));
            if (parts.length > 1) modifiers = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ignored) {
        }
    }
}
