package com.donut.config;

import com.donut.module.ModuleManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named profile presets (e.g. Legit / Raid / Build) stored as JSON in
 * {@code profiles/}. Export/import produces the same JSON format
 * ({@code .donutprofile}).
 */
public final class ProfileManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path profilesDir;
    private static ModuleManager modules;
    private static String active = "Legit";

    private ProfileManager() {
    }

    public static void init(Path configDir, ModuleManager moduleManager) {
        profilesDir = configDir.resolve("profiles");
        modules = moduleManager;
        try {
            Files.createDirectories(profilesDir);
        } catch (IOException e) {
            System.err.println("[Donut] Could not create profiles dir: " + e);
        }
    }

    public static String activeProfile() {
        return active;
    }

    public static List<String> available() {
        List<String> names = new ArrayList<>();
        if (profilesDir != null) {
            try (var stream = Files.list(profilesDir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .forEach(p -> names.add(stripJson(p.getFileName().toString())));
            } catch (IOException ignored) {
            }
        }
        if (!names.contains(active)) names.add(0, active);
        return names;
    }

    /** Saves the current module state under the given name and makes it active. */
    public static void saveProfile(String name) {
        ensureDir();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("format", "donutprofile");
        doc.put("version", Migration.CURRENT);
        doc.put("modules", ModuleStateIO.collect(modules.all()));
        try {
            Path file = profilesDir.resolve(name + ".json");
            Files.writeString(file, GSON.toJson(doc));
            active = name;
            ConfigManager.save();
        } catch (IOException e) {
            System.err.println("[Donut] Failed to save profile " + name + ": " + e);
        }
    }

    /** Loads a profile by name and applies it to all modules. */
    public static void loadProfile(String name) {
        ensureDir();
        Path file = profilesDir.resolve(name + ".json");
        if (!Files.exists(file)) {
            System.err.println("[Donut] Profile not found: " + name);
            return;
        }
        try {
            Map<?, ?> doc = GSON.fromJson(Files.readString(file), Map.class);
            Object modsTable = doc != null ? doc.get("modules") : null;
            if (modsTable instanceof Map<?, ?> m) {
                ModuleStateIO.apply(m, modules.all());
            }
            active = name;
            ConfigManager.save();
        } catch (IOException e) {
            System.err.println("[Donut] Failed to load profile " + name + ": " + e);
        }
    }

    /** Exports the active profile to an arbitrary path as .donutprofile JSON. */
    public static void exportActive(Path destination) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("format", "donutprofile");
        doc.put("version", Migration.CURRENT);
        doc.put("modules", ModuleStateIO.collect(modules.all()));
        try {
            Path target = destination.getFileName().toString().endsWith(".donutprofile")
                    ? destination : destination.resolveSibling(destination.getFileName() + ".donutprofile");
            Files.writeString(target, GSON.toJson(doc));
        } catch (IOException e) {
            System.err.println("[Donut] Failed to export profile: " + e);
        }
    }

    /** Imports a .donutprofile file, applies it, and saves it under its file name. */
    public static void importProfile(Path source) {
        try {
            Map<?, ?> doc = GSON.fromJson(Files.readString(source), Map.class);
            if (doc == null || !"donutprofile".equals(doc.get("format"))) {
                System.err.println("[Donut] Not a valid .donutprofile file: " + source);
                return;
            }
            String name = stripJson(source.getFileName().toString().replace(".donutprofile", ""));
            ensureDir();
            Files.writeString(profilesDir.resolve(name + ".json"), GSON.toJson(doc));
            loadProfile(name);
        } catch (IOException e) {
            System.err.println("[Donut] Failed to import profile: " + e);
        }
    }

    private static void ensureDir() {
        try {
            if (profilesDir != null) Files.createDirectories(profilesDir);
        } catch (IOException ignored) {
        }
    }

    private static String stripJson(String s) {
        return s.endsWith(".json") ? s.substring(0, s.length() - 5) : s;
    }
}
