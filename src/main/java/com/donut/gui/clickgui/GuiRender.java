package com.donut.gui.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Flat rounded-rect drawing helpers shared by all GUI components. Yarn 1.21.1
 * has no DrawContext.fillRoundRect, so rounded corners are composited from
 * three axis-aligned quads plus per-column corner steps.
 */
public final class GuiRender {
    private GuiRender() {
    }

    public static void roundRect(DrawContext g, int x, int y, int w, int h, int radius, int color) {
        if (((color >>> 24) & 0xFF) == 0) return;
        int r = Math.min(radius, Math.min(w, h) / 2);
        // Center band
        g.fill(x, y + r, x + w, y + h - r, color);
        // Top and bottom bands
        g.fill(x + r, y, x + w - r, y + r, color);
        g.fill(x + r, y + h - r, x + w - r, y + h, color);
        // Corners: each is drawn as per-column steps of a quarter circle
        fillCorner(g, x, y, r, r, color, false, false);
        fillCorner(g, x + w - r, y, r, r, color, true, false);
        fillCorner(g, x, y + h - r, r, r, color, false, true);
        fillCorner(g, x + w - r, y + h - r, r, r, color, true, true);
    }

    /** Fills one quarter-circle corner using per-column steps. */
    private static void fillCorner(DrawContext g, int x, int y, int w, int h,
                                   int color, boolean mirrorX, boolean mirrorY) {
        for (int i = 0; i < w; i++) {
            float t = (i + 0.5f) / w;
            int rowH = Math.round(h * (1 - (float) Math.sqrt(1 - t * t)));
            if (rowH <= 0) continue;
            int col = mirrorX ? x + w - 1 - i : x + i;
            if (mirrorY) {
                g.fill(col, y + h - rowH, col + 1, y + h, color);
            } else {
                g.fill(col, y, col + 1, y + rowH, color);
            }
        }
    }

    public static void rect(DrawContext g, int x, int y, int w, int h, int color) {
        if (((color >>> 24) & 0xFF) == 0) return;
        g.fill(x, y, x + w, y + h, color);
    }

    public static void outline(DrawContext g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static void text(DrawContext g, String s, int x, int y, int color, boolean shadow) {
        var tr = MinecraftClient.getInstance().textRenderer;
        if (shadow) {
            g.drawTextWithShadow(tr, s, x, y, color);
        } else {
            g.drawText(tr, s, x, y, color, false);
        }
    }

    public static int textWidth(String s) {
        return MinecraftClient.getInstance().textRenderer.getWidth(s);
    }

    public static String trim(String s, int maxWidth) {
        var tr = MinecraftClient.getInstance().textRenderer;
        if (tr.getWidth(s) <= maxWidth) return s;
        while (s.length() > 1 && tr.getWidth(s + "…") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }
}
