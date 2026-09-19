package com.donut.module;

import com.donut.module.settings.BooleanSetting;
import com.donut.module.settings.NumberSetting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModuleSettingsTest {

    private static Module module() {
        return new Module("Test", "test module", Category.MISC) {
            {
                settings.add(new BooleanSetting("Flag", false, "flag"));
                settings.add(new NumberSetting("Speed", 0.5, 0.0, 1.0, 0.1, 2, "speed"));
            }
        };
    }

    @Test
    void toggleLifecycle() {
        Module m = module();
        assertFalse(m.isEnabled());
        m.enable();
        assertTrue(m.isEnabled());
        m.enable();
        assertTrue(m.isEnabled());
        m.toggle();
        assertFalse(m.isEnabled());
    }

    @Test
    void settingsSerializeRoundTrip() {
        Module m = module();
        m.settings().get("Flag", BooleanSetting.class).set(true);
        m.settings().get("Speed", NumberSetting.class).set(0.9);

        var collected = com.donut.config.ModuleStateIO.collect(List.of(m));
        assertEquals("true", ((Map<?, ?>) collected.get("Test")).get("Flag"));
        assertEquals("0.90", ((Map<?, ?>) collected.get("Test")).get("Speed"));
    }

    @Test
    void numberSettingClampsToRange() {
        NumberSetting n = new NumberSetting("S", 0.5, 0.0, 1.0, 0.1, 1, "s");
        n.set(5.0);
        assertEquals(1.0, n.get(), 1e-9);
        n.set(-3.0);
        assertEquals(0.0, n.get(), 1e-9);
    }

    @Test
    void duplicateModuleNamesRejected() {
        ModuleManager manager = new ModuleManager();
        manager.register(module());
        assertThrows(IllegalArgumentException.class, () -> manager.register(module()));
    }

    @Test
    void keybindToggleViaManager() {
        Module m = module();
        m.keybind.set(org.lwjgl.glfw.GLFW.GLFW_KEY_G);
        ModuleManager manager = new ModuleManager();
        manager.register(m);

        assertNull(manager.handleKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_H, 0));
        assertFalse(m.isEnabled());
        assertEquals(m, manager.handleKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_G, 0));
        assertTrue(m.isEnabled());
    }
}
