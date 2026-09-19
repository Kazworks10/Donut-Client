package com.donut.gui.clickgui;

import com.donut.module.settings.BooleanSetting;
import com.donut.module.settings.ColorSetting;
import com.donut.module.settings.EnumSetting;
import com.donut.module.settings.KeybindSetting;
import com.donut.module.settings.NumberSetting;
import com.donut.module.settings.Setting;
import com.donut.module.settings.StringSetting;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/**
 * Polymorphic setting widgets. Each renders inside a row and returns its
 * height so the panel can stack them; all row heights are fixed for simplicity.
 */
public abstract class SettingComponent {
    protected static final int H = Theme.ROW_H + 2;

    public final Setting<?> setting;
    protected int x, y, w;

    protected SettingComponent(Setting<?> setting) {
        this.setting = setting;
    }

    /** Factory: builds the right widget type for a setting. */
    public static SettingComponent forSetting(Setting<?> s) {
        if (s instanceof BooleanSetting b) return new Toggle(b);
        if (s instanceof NumberSetting n) return new Slider(n);
        if (s instanceof EnumSetting<?> e) return new Dropdown(e);
        if (s instanceof KeybindSetting k) return new Keybind(k);
        if (s instanceof ColorSetting c) return new ColorRow(c);
        if (s instanceof StringSetting st) return new TextField(st);
        return null;
    }

    public boolean visible() {
        return setting.isVisible();
    }

    public void layout(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
    }

    public int height() {
        return H;
    }

    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        GuiRender.text(g, com.donut.gui.clickgui.GuiRender.trim(setting.name, w / 2), x + Theme.PAD, y + 3,
                Theme.TEXT_DIM, false);
    }

    /** Mouse down inside the row; returns true if consumed. */
    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    public boolean mouseDragged(double mx, double my) {
        return false;
    }

    public boolean keyPressed(int keyCode, int mods) {
        return false;
    }

    public boolean charTyped(char chr) {
        return false;
    }

    /** True when this widget wants raw keyboard input (text fields, bind capture). */
    public boolean wantsKeyboard() {
        return false;
    }

    // ---- Toggle (BooleanSetting) -----------------------------------------

    public static final class Toggle extends SettingComponent {
        private final BooleanSetting bool;

        public Toggle(BooleanSetting bool) {
            super(bool);
            this.bool = bool;
        }

        @Override
        public void render(DrawContext g, int mouseX, int mouseY, float delta) {
            super.render(g, mouseX, mouseY, delta);
            int boxW = 22;
            int bx = x + w - boxW - Theme.PAD;
            int by = y + 2;
            GuiRender.roundRect(g, bx, by, boxW, H - 4, 5, bool.get() ? Theme.ACCENT_DIM : Theme.SLIDER_BG);
            int knobX = bool.get() ? bx + boxW - H + 4 : bx + 2;
            GuiRender.roundRect(g, knobX, by + 2, H - 8, H - 8, 3, bool.get() ? Theme.ACCENT : Theme.TEXT_DIM);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && inside(mx, my)) {
                bool.set(!bool.get());
                return true;
            }
            return false;
        }
    }

    // ---- Slider (NumberSetting) -------------------------------------------

    public static final class Slider extends SettingComponent {
        private final NumberSetting num;
        private boolean dragging;

        public Slider(NumberSetting num) {
            super(num);
            this.num = num;
        }

        @Override
        public void render(DrawContext g, int mouseX, int mouseY, float delta) {
            int labelW = w / 2;
            String val = String.format("%." + num.decimals + "f", num.get());
            GuiRender.text(g, GuiRender.trim(num.name, labelW - 8), x + Theme.PAD, y + 3, Theme.TEXT_DIM, false);
            GuiRender.text(g, val, x + w - Theme.PAD - GuiRender.textWidth(val), y + 3, Theme.ACCENT, false);

            int ty = y + H - 4;
            int trackX = x + Theme.PAD;
            int trackW = w - Theme.PAD * 2;
            GuiRender.roundRect(g, trackX, ty, trackW, 3, 1, Theme.SLIDER_BG);
            float frac = (float) ((num.get() - num.min) / (num.max - num.min));
            int fillW = Math.round(trackW * Math.clamp(frac, 0f, 1f));
            GuiRender.roundRect(g, trackX, ty, Math.max(fillW, 3), 3, 1, Theme.SLIDER_FILL);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && my >= y && my < y + H && mx >= x && mx < x + w) {
                dragging = true;
                apply(mx);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            dragging = false;
            return false;
        }

        @Override
        public boolean mouseDragged(double mx, double my) {
            if (dragging) {
                apply(mx);
                return true;
            }
            return false;
        }

        private void apply(double mx) {
            double frac = Math.clamp((mx - (x + Theme.PAD)) / (double) (w - Theme.PAD * 2), 0.0, 1.0);
            double raw = num.min + frac * (num.max - num.min);
            double stepped = Math.round(raw / num.step) * num.step;
            num.set(stepped);
        }
    }

    // ---- Dropdown (EnumSetting) -------------------------------------------

    public static final class Dropdown extends SettingComponent {
        private final EnumSetting<?> enumSetting;
        private boolean open;
        private double lastMouseX, lastMouseY;

        public Dropdown(EnumSetting<?> e) {
            super(e);
            this.enumSetting = e;
        }

        @Override
        public void render(DrawContext g, int mouseX, int mouseY, float delta) {
            super.render(g, mouseX, mouseY, delta);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            String cur = enumSetting.get().toString();
            int valX = x + w / 2;
            GuiRender.text(g, GuiRender.trim(cur, w / 2 - Theme.PAD * 2 - 8), valX, y + 3, Theme.ACCENT, false);
            GuiRender.text(g, open ? "▲" : "▼", x + w - Theme.PAD - 6, y + 3, Theme.TEXT_DIM, false);
        }

        /** Drawn last by the panel so the value list floats above other rows. */
        public void renderOverlay(DrawContext g, int mouseX, int mouseY) {
            if (!open) {
                mouseX = (int) lastMouseX;
                mouseY = (int) lastMouseY;
            }
            Object[] vals = enumSetting.values();
            int oy = y + H;
            GuiRender.roundRect(g, x, oy, w, vals.length * Theme.ROW_H + 4, 4, Theme.HEADER);
            GuiRender.outline(g, x, oy, w, vals.length * Theme.ROW_H + 4, Theme.OUTLINE);
            for (int i = 0; i < vals.length; i++) {
                int ry = oy + 2 + i * Theme.ROW_H;
                boolean sel = vals[i].equals(enumSetting.get());
                boolean hover = hovering(mouseX, mouseY, ry);
                if (hover) GuiRender.rect(g, x + 1, ry, w - 2, Theme.ROW_H, Theme.ROW_HOVER);
                GuiRender.text(g, GuiRender.trim(vals[i].toString(), w - Theme.PAD * 2),
                        x + Theme.PAD, ry + 3, sel ? Theme.ACCENT : Theme.TEXT, false);
            }
        }

        private boolean hovering(double mx, double my, int ry) {
            return mx >= x + 1 && mx < x + w - 1 && my >= ry && my < ry + Theme.ROW_H;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0) return false;
            if (open) {
                Object[] vals = enumSetting.values();
                int row = (int) ((my - (y + H + 2)) / Theme.ROW_H);
                if (row >= 0 && row < vals.length && mx >= x && mx < x + w) {
                    setEnum(vals[row]);
                    open = false;
                    return true;
                }
            }
            if (inside(mx, my)) {
                open = !open;
                return true;
            }
            return false;
        }

        public boolean isOpen() {
            return open;
        }

        public void close() {
            open = false;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void setEnum(Object value) {
            ((EnumSetting) enumSetting).set((Enum) value);
        }
    }

    // ---- Keybind capture ---------------------------------------------------

    public static final class Keybind extends SettingComponent {
        private final KeybindSetting bind;

        public Keybind(KeybindSetting bind) {
            super(bind);
            this.bind = bind;
        }

        @Override
        public void render(DrawContext g, int mouseX, int mouseY, float delta) {
            super.render(g, mouseX, mouseY, delta);
            String label = bind.listening ? "> press key <" : bind.display();
            int color = bind.listening ? Theme.ACCENT : Theme.TEXT;
            GuiRender.text(g, label, x + w - Theme.PAD - GuiRender.textWidth(label), y + 3, color, false);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && inside(mx, my)) {
                bind.listening = !bind.listening;
                if (bind.listening) bind.set(0); // clear while waiting
                return true;
            }
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int mods) {
            if (!bind.listening) return false;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                bind.set(0);
                bind.modifiers = 0;
            } else {
                bind.set(keyCode);
                bind.modifiers = mods & (1 | 2 | 4);
            }
            bind.listening = false;
            return true;
        }

        @Override
        public boolean wantsKeyboard() {
            return bind.listening;
        }
    }

    // ---- Color row (ColorSetting): click cycles a small palette ------------

    public static final class ColorRow extends SettingComponent {
        private static final int[] SWATCHES = {
                0xFFEAE6FF, 0xFFB478FF, 0xFF7CE38B, 0xFFE15A5A,
                0xFF5AB0E1, 0xFFE0B25A, 0xFFFFFFFF, 0xFF221A3E
        };
        private final ColorSetting color;
        private boolean open;

        public ColorRow(ColorSetting color) {
            super(color);
            this.color = color;
        }

        @Override
        public void render(DrawContext g, int mouseX, int mouseY, float delta) {
            super.render(g, mouseX, mouseY, delta);
            int sw = H - 5;
            int sx = x + w - sw - Theme.PAD;
            GuiRender.roundRect(g, sx, y + 2, sw, sw, 3, color.get());
            GuiRender.outline(g, sx, y + 2, sw, sw, Theme.OUTLINE);
            if (open) {
                for (int i = 0; i < SWATCHES.length; i++) {
                    int cxx = x + w - sw - Theme.PAD - (i + 1) * (sw + 2);
                    GuiRender.roundRect(g, cxx, y + 2, sw, sw, 3, SWATCHES[i]);
                    GuiRender.outline(g, cxx, y + 2, sw, sw, Theme.OUTLINE);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || my < y || my >= y + H) return false;
            int sw = H - 5;
            int sx = x + w - sw - Theme.PAD;
            if (mx >= sx && mx <= sx + sw) {
                open = !open;
                return true;
            }
            if (open) {
                for (int i = 0; i < SWATCHES.length; i++) {
                    int cxx = x + w - sw - Theme.PAD - (i + 1) * (sw + 2);
                    if (mx >= cxx && mx <= cxx + sw) {
                        int rgb = SWATCHES[i] & 0xFFFFFF;
                        color.setRgb((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF);
                        open = false;
                        return true;
                    }
                }
                open = false;
            }
            return false;
        }
    }

    // ---- Text field (StringSetting) -----------------------------------------

    public static final class TextField extends SettingComponent {
        private final StringSetting string;
        private boolean focused;
        private String draft;

        public TextField(StringSetting string) {
            super(string);
            this.string = string;
        }

        @Override
        public void render(DrawContext g, int mouseX, int mouseY, float delta) {
            super.render(g, mouseX, mouseY, delta);
            String shown = focused ? draft : string.get();
            String cursor = focused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "";
            String text = GuiRender.trim(shown + cursor, w / 2 - Theme.PAD * 2);
            GuiRender.rect(g, x + w / 2 - 2, y + 2, w / 2 + 2 - Theme.PAD, H - 4, Theme.INPUT_BG);
            GuiRender.text(g, text, x + w / 2, y + 3, focused ? Theme.ACCENT : Theme.TEXT, false);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && inside(mx, my)) {
                if (!focused) {
                    focused = true;
                    draft = string.get();
                }
                return true;
            }
            if (focused) commit();
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int mods) {
            if (!focused) return false;
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                focused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !draft.isEmpty()) {
                draft = draft.substring(0, draft.length() - 1);
                return true;
            }
            return true; // consume all keys while focused
        }

        @Override
        public boolean charTyped(char chr) {
            if (!focused) return false;
            draft += chr;
            return true;
        }

        private void commit() {
            if (draft != null) string.set(draft.trim());
            focused = false;
        }

        @Override
        public boolean wantsKeyboard() {
            return focused;
        }
    }

    protected boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + H;
    }
}
