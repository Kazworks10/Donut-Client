package com.donut.gui.clickgui;

import com.donut.config.ConfigManager;
import com.donut.module.Category;
import com.donut.module.Module;
import com.donut.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The ClickGUI: draggable per-category panels, left-click toggles a module,
 * right-click expands its settings, live search filters modules, and the GUI
 * open key closes it. Layout is computed each frame; only positions persist.
 */
public final class ClickGUI extends Screen {
    private static final int PANEL_GAP = 6;
    private static final int SEARCH_W = 70;

    private final ModuleManager modules;
    private final List<Panel> panels = new ArrayList<>();

    // Dragging state
    private Panel dragPanel;
    private int dragOffX, dragOffY;
    private int nextPanelX = 8, nextPanelY = 24;

    // Search
    private boolean searching;
    private String query = "";
    private boolean anyExpandedDirty;

    public ClickGUI(ModuleManager modules) {
        super(Text.literal("Donut ClickGUI"));
        this.modules = modules;
        for (Category cat : Category.values()) {
            panels.add(new Panel(cat, modulesByCategory(cat)));
        }
        // Stagger initial panel positions
        int px = 8;
        for (Panel p : panels) {
            p.x = px;
            p.y = 24;
            px += Theme.PANEL_W + PANEL_GAP;
        }
    }

    private List<Module> modulesByCategory(Category cat) {
        List<Module> out = new ArrayList<>();
        for (Module m : modules.all()) if (m.category() == cat) out.add(m);
        return out;
    }

    @Override
    protected void init() {
        anyExpandedDirty = false;
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        // Scrim
        g.fill(0, 0, width, height, Theme.SCRIM);

        // Header strip
        GuiRender.roundRect(g, 4, 4, width - 8, 16, 6, Theme.HEADER);
        GuiRender.text(g, "DONUT CLIENT", 10, 8, Theme.ACCENT, true);
        String searchLabel = searching ? query + ((tickCounter / 10) % 2 == 0 ? "_" : "") : "Search... [TAB]";
        int sw = GuiRender.textWidth(searchLabel) + 8;
        int sx = width - sw - 10;
        GuiRender.roundRect(g, sx, 6, sw, 12, 5, Theme.INPUT_BG);
        GuiRender.text(g, searchLabel, sx + 4, 9, searching ? Theme.ACCENT : Theme.TEXT_DIM, false);

        for (Panel p : panels) {
            p.render(g, mouseX, mouseY, delta, query);
        }
        // Dropdown overlays render last so they float above later panels
        for (Panel p : panels) {
            p.renderOverlays(g, mouseX, mouseY);
        }

        tickCounter++;
    }

    private int tickCounter;

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Search box hit?
        int sw = SEARCH_W;
        int sx = width - sw - 10;
        if (my >= 6 && my < 18 && mx >= sx && mx < sx + sw) {
            searching = true;
            return true;
        }

        // Panels (iterate topmost-last for correct hit priority)
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (p.mouseClicked(mx, my, button)) return true;
            if (p.headerContains(mx, my)) {
                // Start dragging; bring to front
                panels.remove(p);
                panels.add(p);
                dragPanel = p;
                dragOffX = (int) mx - p.x;
                dragOffY = (int) my - p.y;
                return true;
            }
        }

        // Click elsewhere: stop searching, close dropdowns
        searching = false;
        query = "";
        for (Panel p : panels) p.closeDropdowns();
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragPanel = null;
        for (Panel p : panels) p.mouseReleased(mx, my, button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragPanel != null) {
            dragPanel.x = Math.clamp((int) mx - dragOffX, 0, width - Theme.PANEL_W);
            dragPanel.y = Math.clamp((int) my - dragOffY, 0, height - 20);
            return true;
        }
        for (Panel p : panels) {
            if (p.mouseDragged(mx, my)) return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        for (Panel p : panels) {
            if (p.contains(mx, my)) {
                p.scrollBy((int) (vertical * 12));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Any expanded keybind/text widgets capture first
        for (Panel p : panels) {
            if (p.keyPressed(keyCode, modifiers)) return true;
        }
        if (searching) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_TAB) {
                searching = false;
                query = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                return true;
            }
            return true; // consume everything else; chars arrive via charTyped
        }
        if (keyCode == ConfigManager.guiOpenKey) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            searching = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searching) {
            query += Character.toLowerCase(chr);
            return true;
        }
        for (Panel p : panels) {
            if (p.charTyped(chr)) return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        ConfigManager.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---- Panel ------------------------------------------------------------

    private final class Panel {
        final Category category;
        final List<ModuleButton> buttons = new ArrayList<>();
        int x, y;
        int scroll;
        int contentH; // computed during render

        Panel(Category category, List<Module> mods) {
            this.category = category;
            for (Module m : mods) buttons.add(new ModuleButton(m));
        }

        int headerHeight() {
            return Theme.HEADER_H;
        }

        boolean headerContains(double mx, double my) {
            return mx >= x && mx < x + Theme.PANEL_W && my >= y && my < y + headerHeight();
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + Theme.PANEL_W && my >= y
                    && my < y + panelHeight();
        }

        int panelHeight() {
            return Math.min(headerHeight() + contentH, Theme.MAX_CONTENT_H) ;
        }

        void scrollBy(int delta) {
            scroll = Math.clamp(scroll + delta, 0, Math.max(0, contentH - (Theme.MAX_CONTENT_H - headerHeight())));
        }

        boolean matchesQuery(ModuleButton mb) {
            if (query.isEmpty()) return true;
            if (mb.module.name().toLowerCase().contains(query)) return true;
            for (SettingComponent c : mb.components()) {
                if (c.setting.name.toLowerCase().contains(query)) return true;
            }
            return false;
        }

        void render(DrawContext g, int mx, int my, float delta, String q) {
            int ph = panelHeight();
            GuiRender.roundRect(g, x, y, Theme.PANEL_W, ph, 6, Theme.PANEL);
            GuiRender.outline(g, x, y, Theme.PANEL_W, ph, Theme.OUTLINE);
            GuiRender.roundRect(g, x, y, Theme.PANEL_W, headerHeight(), 6, Theme.HEADER);
            GuiRender.rect(g, x, y + headerHeight() - 2, Theme.PANEL_W, 2, Theme.ACCENT_DIM);
            GuiRender.text(g, GuiRender.trim(category.displayName, Theme.PANEL_W - 12),
                    x + Theme.PAD, y + 4, Theme.TEXT, true);

            // Layout visible buttons with scrolling clip
            int clipTop = y + headerHeight();
            int clipBottom = y + ph;
            g.enableScissor(x, clipTop, x + Theme.PANEL_W, clipBottom);
            int cy = clipTop - scroll;
            contentH = 0;
            for (ModuleButton mb : buttons) {
                if (!matchesQuery(mb)) continue;
                mb.layout(x, cy, Theme.PANEL_W);
                mb.render(g, mx, my, delta);
                cy += mb.height();
                contentH += mb.height();
            }
            if (contentH == 0) {
                GuiRender.text(g, "no matches", x + Theme.PAD, cy + 2, Theme.TEXT_DIM, false);
                contentH = 16;
            }
            g.disableScissor();
        }

        void renderOverlays(DrawContext g, int mx, int my) {
            for (ModuleButton mb : buttons) {
                for (SettingComponent c : mb.components()) {
                    if (c instanceof SettingComponent.Dropdown d) d.renderOverlay(g, mx, my);
                }
            }
        }

        boolean mouseClicked(double mx, double my, int button) {
            for (ModuleButton mb : buttons) {
                if (!matchesQuery(mb)) continue;
                // Settings widgets first (they sit below the header row)
                for (SettingComponent c : mb.components()) {
                    if (!c.visible()) continue;
                    if (c.mouseClicked(mx, my, button)) return true;
                }
                if (mb.headerClicked(mx, my, button)) {
                    mb.onClick(button);
                    return true;
                }
            }
            return false;
        }

        void mouseReleased(double mx, double my, int button) {
            for (ModuleButton mb : buttons) {
                for (SettingComponent c : mb.components()) c.mouseReleased(mx, my, button);
            }
        }

        boolean mouseDragged(double mx, double my) {
            for (ModuleButton mb : buttons) {
                for (SettingComponent c : mb.components()) {
                    if (c.mouseDragged(mx, my)) return true;
                }
            }
            return false;
        }

        boolean keyPressed(int keyCode, int modifiers) {
            for (ModuleButton mb : buttons) {
                for (SettingComponent c : mb.components()) {
                    if (c.keyPressed(keyCode, modifiers)) return true;
                }
            }
            return false;
        }

        boolean charTyped(char chr) {
            for (ModuleButton mb : buttons) {
                for (SettingComponent c : mb.components()) {
                    if (c.charTyped(chr)) return true;
                }
            }
            return false;
        }

        void closeDropdowns() {
            for (ModuleButton mb : buttons) {
                for (SettingComponent c : mb.components()) {
                    if (c instanceof SettingComponent.Dropdown d) d.close();
                }
            }
        }
    }
}
