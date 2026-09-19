package com.donut.gui.clickgui;

import com.donut.module.Module;
import com.donut.module.settings.Setting;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * One module row inside a category panel: left-click toggles, right-click
 * slides in the settings list. Drawn as a row plus optional expanded body.
 */
public final class ModuleButton {
    public final Module module;
    private final List<SettingComponent> components = new ArrayList<>();
    private boolean expanded;
    private float expandAnim = 0f; // 0..1 for the slide-in feel

    private int x, y, w;
    private boolean hovered;

    public ModuleButton(Module module) {
        this.module = module;
        for (Setting<?> s : module.settings()) {
            SettingComponent c = SettingComponent.forSetting(s);
            if (c != null) components.add(c);
        }
        for (Module.Action a : module.actions()) {
            components.add(new SettingComponent.ActionButton(a));
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    /** Height of the full row (collapsed row or expanded row + settings). */
    public int height() {
        if (!expanded) return Theme.ROW_H;
        int h = Theme.ROW_H;
        for (SettingComponent c : components) {
            if (c.visible()) h += c.height();
        }
        return h;
    }

    public void layout(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
        int cy = y + Theme.ROW_H;
        if (expanded) {
            for (SettingComponent c : components) {
                if (!c.visible()) continue;
                c.layout(x, cy, w);
                cy += c.height();
            }
        }
    }

    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + Theme.ROW_H;
        int bg = expanded ? Theme.ROW_EXPANDED : hovered ? Theme.ROW_HOVER : Theme.ROW;
        GuiRender.rect(g, x, y, w, Theme.ROW_H, bg);

        // Toggle indicator: small filled square, accent when on
        int ind = 8;
        int ix = x + Theme.PAD;
        int iy = y + (Theme.ROW_H - ind) / 2;
        GuiRender.rect(g, ix, iy, ind, ind, module.isEnabled() ? Theme.ACCENT : Theme.DISABLED);
        if (module.isEnabled()) GuiRender.outline(g, ix, iy, ind, ind, Theme.ACCENT);

        int textX = ix + ind + 4;
        int nameColor = module.isEnabled() ? Theme.TEXT : Theme.TEXT_DIM;
        GuiRender.text(g, GuiRender.trim(module.name(), w - 2 * Theme.PAD - 20), textX, y + 3, nameColor, false);

        // Expand chevron on the right
        String chev = expanded ? "▲" : "▼";
        GuiRender.text(g, chev, x + w - Theme.PAD - GuiRender.textWidth(chev), y + 3, Theme.TEXT_DIM, false);

        if (!expanded) return;

        // Settings body: slightly inset panel
        int bodyH = height() - Theme.ROW_H;
        GuiRender.rect(g, x + 2, y + Theme.ROW_H, w - 4, bodyH, Theme.INPUT_BG);
        for (SettingComponent c : components) {
            if (!c.visible()) continue;
            c.render(g, mouseX, mouseY, delta);
        }
    }

    /** Click inside the collapsed/expanded row header. */
    public boolean headerClicked(double mx, double my, int button) {
        return mx >= x && mx < x + w && my >= y && my < y + Theme.ROW_H;
    }

    public void onClick(int button) {
        if (button == 0) {
            module.toggle();
        } else if (button == 1) {
            expanded = !expanded;
        }
    }

    public List<SettingComponent> components() {
        return components;
    }
}
