package com.donut.module;

import com.donut.event.EventBus;
import com.donut.event.events.TickEvent;
import com.donut.module.settings.KeybindSetting;
import com.donut.module.settings.Settings;

import java.util.function.Consumer;

/**
 * Base class for all modules.
 * <p>
 * Modules register event listeners through {@link #listen(Class, Consumer)} at any time
 * (typically in {@link #init()}); those listeners are live only while the module is
 * enabled. Per-tick logic can simply override {@link #onTick()}. Every module carries a
 * built-in {@link #keybind} setting used by the GUI keybind editor and the global key handler.
 */
public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    protected final Settings settings = new Settings();
    public final KeybindSetting keybind;

    private boolean enabled;
    private EventBus bus;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keybind = new KeybindSetting("Keybind", 0, 0, "Toggle keybind");
        this.settings.add(keybind);
    }

    // ---- lifecycle -------------------------------------------------------

    /** Called once after construction, before any config is applied. */
    protected void init() {
    }

    public final void setBus(EventBus bus) {
        this.bus = bus;
        init();
    }

    public final boolean enable() {
        if (enabled) return false;
        enabled = true;
        onEnable();
        return true;
    }

    public final boolean disable() {
        if (!enabled) return false;
        enabled = false;
        onDisable();
        return true;
    }

    public final void toggle() {
        if (enabled) disable();
        else enable();
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /** Convenience hook: runs at the end of every client tick while enabled. */
    protected void onTick() {
    }

    /**
     * Optional HUD line while the module is active (e.g. current activity).
     * Default is null — no line. Called on the client/render thread.
     */
    public String statusLine() {
        return null;
    }

    final void tick() {
        if (enabled) onTick();
    }

    // ---- events ----------------------------------------------------------

    protected final <T> void listen(Class<T> type, Consumer<T> handler) {
        if (bus == null) {
            throw new IllegalStateException("Module " + name + " listened before setBus()");
        }
        bus.subscribe(type, e -> {
            if (enabled) handler.accept(e);
        });
    }

    /** Subscribes the module's {@link #onTick()} to tick events while enabled. */
    protected final void listenTick() {
        listen(TickEvent.class, e -> onTick());
    }

    // ---- accessors -------------------------------------------------------

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    public final Category category() {
        return category;
    }

    public final Settings settings() {
        return settings;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    @Override
    public final String toString() {
        return name;
    }
}
