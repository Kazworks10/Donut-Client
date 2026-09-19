package com.donut.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MigrationTest {

    @Test
    void v1ConfigMigratesSymbolicKeyToNumeric() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        Map<String, Object> gui = new LinkedHashMap<>();
        gui.put("open_key", "RSHIFT");
        root.put("gui", gui);

        Migration.migrate(root);

        assertEquals(2, root.get("version"));
        @SuppressWarnings("unchecked")
        Map<String, Object> migrated = (Map<String, Object>) root.get("gui");
        assertEquals(344, migrated.get("key"));  // GLFW_KEY_RIGHT_SHIFT
        assertEquals(1, migrated.get("mods"));   // shift
        assertFalse(migrated.containsKey("open_key"));
    }

    @Test
    void v1ConfigWithUnknownKeyFallsBackToRightShift() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        Map<String, Object> gui = new LinkedHashMap<>();
        gui.put("open_key", "F13");
        root.put("gui", gui);

        Migration.migrate(root);

        @SuppressWarnings("unchecked")
        Map<String, Object> migrated = (Map<String, Object>) root.get("gui");
        assertEquals(344, migrated.get("key"));
    }

    @Test
    void v2ConfigIsUntouched() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 2);
        Map<String, Object> gui = new LinkedHashMap<>();
        gui.put("key", 96); // grave
        gui.put("mods", 0);
        root.put("gui", gui);

        Migration.migrate(root);

        @SuppressWarnings("unchecked")
        Map<String, Object> guiOut = (Map<String, Object>) root.get("gui");
        assertEquals(96, guiOut.get("key"));
        assertEquals(0, guiOut.get("mods"));
    }

    @Test
    void missingVersionDefaultsToV1AndMigrates() {
        Map<String, Object> root = new LinkedHashMap<>();
        Migration.migrate(root);
        assertEquals(2, root.get("version"));
    }

    @Test
    void futureVersionIsPreservedNotDowngraded() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 99);
        root.put("custom", "keep-me");
        Migration.migrate(root);
        assertEquals(99, root.get("version"));
        assertEquals("keep-me", root.get("custom"));
    }
}
