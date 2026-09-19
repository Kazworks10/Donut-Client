package com.donut.pathfinding;

/**
 * Client-side movement input override. When active, the player's movement input
 * is driven by this controller instead of the keyboard (modules set the fields
 * directly on the input via the input mixin).
 * <p>
 * Deliberately simple: the executor computes desired forward/strafe each tick;
 * jump/sneak/sprint are booleans. Vanilla physics handle the rest.
 */
public final class MovementInputOverride {
    private static boolean active;
    private static float forward;
    private static float strafe;
    private static boolean jump, sneak, sprint;

    private MovementInputOverride() {
    }

    public static void begin() {
        active = true;
        stopMotion();
    }

    public static void end() {
        active = false;
        stopMotion();
    }

    public static boolean isActive() {
        return active;
    }

    /** Sets the desired movement for this tick, in player-local axes [-1, 1]. */
    public static void set(float forwardInput, float strafeInput, boolean jumpInput, boolean sneakInput, boolean sprintInput) {
        forward = clamp(forwardInput);
        strafe = clamp(strafeInput);
        jump = jumpInput;
        sneak = sneakInput;
        sprint = sprintInput;
    }

    public static void stopMotion() {
        forward = 0;
        strafe = 0;
        jump = false;
        sneak = false;
        sprint = false;
    }

    /** Alias used by consumers that halt driving without ending the session. */
    public static void stop() {
        stopMotion();
    }

    public static float forward() {
        return forward;
    }

    public static float strafe() {
        return strafe;
    }

    public static boolean jump() {
        return jump;
    }

    public static boolean sneak() {
        return sneak;
    }

    public static boolean sprint() {
        return sprint;
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }
}
