package com.donut.event.events;

import net.minecraft.client.MinecraftClient;

/** Fired at the end of every client tick, on the render/client thread. */
public record TickEvent(MinecraftClient client) {
}
