package com.donut.gui.clickgui;

/**
 * Tiny frame-rate-independent animator: moves {@code current} toward
 * {@code target} exponentially each frame. Used for scroll and hover effects.
 */
public final class Animation {
    private float current;
    private float target;
    private final float speed;

    public Animation(float start, float speed) {
        this.current = start;
        this.target = start;
        this.speed = speed;
    }

    public void set(float newTarget) {
        this.target = newTarget;
    }

    public void snap(float value) {
        this.current = value;
        this.target = value;
    }

    public float value() {
        return current;
    }

    public boolean done() {
        return Math.abs(current - target) < 0.002f;
    }

    /** Advances the animation; delta is frame time in seconds. */
    public void update(float delta) {
        float t = Math.clamp(delta * speed, 0f, 1f);
        current += (target - current) * t;
    }
}
