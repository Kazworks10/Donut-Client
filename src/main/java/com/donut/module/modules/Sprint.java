package com.donut.module.modules;

import com.donut.module.Category;
import com.donut.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Keeps the sprint key held so the client's own "toggle sprint" logic never
 * engages — a native rewrite of the reference Sprint module (the original used
 * reflection into options internals, which this codebase avoids).
 */
public final class Sprint extends Module {
    private boolean wasPressed;

    public Sprint() {
        super("Sprint", "Always sprinting", Category.MOVEMENT);
    }

    @Override
    protected void init() {
        listenTick();
    }

    @Override
    protected void onEnable() {
        wasPressed = MinecraftClient.getInstance().options.sprintKey.isPressed();
    }

    @Override
    protected void onDisable() {
        MinecraftClient.getInstance().options.sprintKey.setPressed(wasPressed);
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.isUsingSpyglass()) return;
        client.options.sprintKey.setPressed(true);
    }
}
