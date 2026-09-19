package com.donut.rotation;

/**
 * Angle math helpers: shortest-path wrapping, delta computation and
 * yaw/pitch calculation toward a world position.
 */
public final class RotationUtils {
    private RotationUtils() {
    }

    /** Wraps an angle in degrees to [-180, 180). */
    public static float wrapDegrees(float deg) {
        float d = deg % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    /** Shortest signed delta from a to b, in [-180, 180). */
    public static float delta(float from, float to) {
        return wrapDegrees(to - from);
    }

    /** Interpolates along the shortest arc between two angles. */
    public static float lerpAngle(float from, float to, float t) {
        return from + delta(from, to) * t;
    }

    /** True if a full rotation to (yaw, pitch) would complete within maxDegrees. */
    public static boolean withinRange(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float maxDegrees) {
        float dy = Math.abs(delta(currentYaw, targetYaw));
        float dp = Math.abs(targetPitch - currentPitch);
        return dy <= maxDegrees && dp <= maxDegrees;
    }

    /** Normalized direction vector from an eye position toward a target point. */
    public static double[] directionTo(double ex, double ey, double ez, double tx, double ty, double tz) {
        double dx = tx - ex;
        double dy = ty - ey;
        double dz = tz - ez;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) return new double[]{0, 0};
        return new double[]{dx / len, dy / len, dz / len};
    }

    /** Computes yaw (degrees) for a normalized direction (dx, dz). */
    public static float yawFromDirection(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
    }

    /** Computes pitch (degrees) for a normalized direction (dx, dy, dz). */
    public static float pitchFromDirection(double dx, double dy, double dz) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }
}
