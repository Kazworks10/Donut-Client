package com.donut.module.modules;

import com.donut.module.Category;
import com.donut.module.Module;
import com.donut.module.settings.BooleanSetting;
import com.donut.module.settings.ColorSetting;
import com.donut.module.settings.EnumSetting;

/**
 * HUD module: enabled-module list, FPS and coordinates. Rendered by the
 * HudRenderCallback registered in the client entrypoint; this module only owns
 * the settings.
 */
public final class HudOverlay extends Module {
    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private final EnumSetting<Corner> corner = new EnumSetting<>("Corner", Corner.TOP_LEFT, "HUD anchor");
    private final BooleanSetting showModules = new BooleanSetting("Module List", true, "Show enabled modules");
    private final BooleanSetting showFps = new BooleanSetting("FPS", true, "Show FPS");
    private final BooleanSetting showCoords = new BooleanSetting("Coordinates", true, "Show XYZ");
    private final ColorSetting color = new ColorSetting("Text Color", 0xE8E8F0F0, "HUD text color");

    public HudOverlay() {
        super("HudOverlay", "On-screen info overlay", Category.RENDER);
        settings.add(corner);
        settings.add(showModules);
        settings.add(showFps);
        settings.add(showCoords);
        settings.add(color);
    }

    public boolean showModules() {
        return isEnabled() && showModules.get();
    }

    public boolean showFps() {
        return isEnabled() && showFps.get();
    }

    public boolean showCoords() {
        return isEnabled() && showCoords.get();
    }

    public Corner corner() {
        return corner.get();
    }

    public int textColor() {
        return color.get();
    }
}
