package com.donut.config;

import com.donut.event.ConfigReloadedEvent;
import com.donut.event.EventBus;
import com.donut.module.ModuleManager;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads and saves config.toml: [client] metadata, [gui] open-key, and one table
 * per module with its enabled flag and settings. Pure static state keeps access
 * simple for modules and GUI.
 */
public final class ConfigManager {
    public static int guiOpenKey = 344;   // GLFW_KEY_RIGHT_SHIFT
    public static int guiOpenMods = 1;    // shift

    private static Path configPath;
    private static ModuleManager modules;
    private static EventBus bus;
    private static volatile boolean suppressSave;

    private ConfigManager() {
    }

    public static void init(Path configDir, ModuleManager moduleManager, EventBus eventBus) {
        configPath = configDir.resolve("config.toml");
        modules = moduleManager;
        bus = eventBus;
        load();
        ConfigHotReloader.start(configPath, ConfigManager::reloadFromDisk);
    }

    private static void load() {
        Map<String, Object> root = new LinkedHashMap<>();
        if (Files.exists(configPath)) {
            try {
                root = new Toml().read(configPath.toFile()).toMap();
            } catch (Exception e) {
                System.err.println("[Donut] Failed to parse config.toml, using defaults: " + e);
                root = new LinkedHashMap<>();
            }
        }
        Migration.migrate(root);
        applyRoot(root);
        if (!Files.exists(configPath)) save();
    }

    /** Re-reads the file and applies it; called by the hot-reload watcher. */
    public static void reloadFromDisk() {
        suppressSave = true;
        try {
            load();
            if (bus != null) bus.post(new ConfigReloadedEvent("hot-reload"));
        } finally {
            suppressSave = false;
        }
    }

    private static void applyRoot(Map<String, Object> root) {
        Object gui = root.get("gui");
        if (gui instanceof Map<?, ?> g) {
            if (g.get("key") instanceof Number n) guiOpenKey = n.intValue();
            if (g.get("mods") instanceof Number n) guiOpenMods = n.intValue();
        }
        Object modsTable = root.get("modules");
        if (modsTable instanceof Map<?, ?> m && modules != null) {
            ModuleStateIO.apply(m, modules.all());
        }
    }

    public static void save() {
        if (suppressSave || configPath == null) return;
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", Migration.CURRENT);
        Map<String, Object> client = new LinkedHashMap<>();
        client.put("name", "Donut Client");
        client.put("profile", ProfileManager.activeProfile());
        root.put("client", client);
        Map<String, Object> gui = new LinkedHashMap<>();
        gui.put("key", guiOpenKey);
        gui.put("mods", guiOpenMods);
        root.put("gui", gui);
        if (modules != null) root.put("modules", ModuleStateIO.collect(modules.all()));
        try {
            Path parent = configPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            new TomlWriter().write(root, configPath.toFile());
        } catch (IOException e) {
            System.err.println("[Donut] Failed to save config.toml: " + e);
        }
    }
}
