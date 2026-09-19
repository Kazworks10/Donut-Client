package com.donut.gui.hud;

import com.donut.gui.clickgui.GuiRender;
import com.donut.module.Module;
import com.donut.module.ModuleManager;
import com.donut.module.modules.HudOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Corner-anchored info HUD: FPS, coordinates and the enabled-module list.
 * Rendered via HudRenderCallback from the entrypoint; hidden behind the
 * debug screen to avoid duplication.
 */
public final class HudRenderer {
    private static final int PAD = 4;
    private static final int LINE = 10;

    private final ModuleManager modules;
    private final HudOverlay settings;

    public HudRenderer(ModuleManager modules, HudOverlay settings) {
        this.modules = modules;
        this.settings = settings;
    }

    public void render(DrawContext g, MinecraftClient client) {
        if (!settings.isEnabled() || client.player == null) return;
        if (client.getDebugHud().shouldShowDebugHud()) return;

        List<String> lines = new java.util.ArrayList<>();
        if (settings.showFps()) lines.add(client.getCurrentFps() + " fps");
        if (settings.showCoords()) {
            lines.add(String.format("%.1f / %.1f / %.1f",
                    client.player.getX(), client.player.getY(), client.player.getZ()));
        }
        if (settings.showModules()) {
            List<Module> enabled = modules.enabled();
            for (Module m : enabled) {
                lines.add(m.name());
                if (settings.showStatus()) {
                    String s = m.statusLine();
                    if (s != null) lines.add("  " + s);
                }
            }
        }
        if (lines.isEmpty()) return;

        int w = 0;
        for (String l : lines) w = Math.max(w, GuiRender.textWidth(l));
        int h = lines.size() * LINE;
        int x, y;
        switch (settings.corner()) {
            case TOP_RIGHT -> { x = g.getScaledWindowWidth() - w - PAD * 2; y = PAD; }
            case BOTTOM_LEFT -> { x = PAD; y = g.getScaledWindowHeight() - h - PAD; }
            case BOTTOM_RIGHT -> { x = g.getScaledWindowWidth() - w - PAD * 2; y = g.getScaledWindowHeight() - h - PAD; }
            default -> { x = PAD; y = PAD; }
        }

        GuiRender.roundRect(g, x, y, w + PAD * 2, h + PAD, 4, 0x90000000);
        int ly = y + PAD / 2 + 1;
        for (String line : lines) {
            GuiRender.text(g, line, x + PAD, ly, settings.textColor(), true);
            ly += LINE;
        }
    }
}
