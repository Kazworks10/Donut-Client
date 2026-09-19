package com.donut.event;

/** Fired after config.toml was re-read from disk (hot reload). */
public record ConfigReloadedEvent(String source) {
}
