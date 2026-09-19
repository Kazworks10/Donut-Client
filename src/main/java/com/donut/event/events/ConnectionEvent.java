package com.donut.event.events;

/** Fired when the client joins or leaves a server/world. */
public record ConnectionEvent(boolean joined, String serverAddress) {
}
