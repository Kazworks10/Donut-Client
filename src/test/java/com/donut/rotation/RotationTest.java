package com.donut.rotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RotationTest {

    @Test
    void wrapDegreesStaysInRange() {
        assertEquals(0f, RotationUtils.wrapDegrees(0f), 1e-6);
        assertEquals(180f, Math.abs(RotationUtils.wrapDegrees(180f)), 1e-6);
        assertEquals(180f, Math.abs(RotationUtils.wrapDegrees(-180f)), 1e-6);
        assertEquals(-10f, RotationUtils.wrapDegrees(350f), 1e-6);
        assertEquals(10f, RotationUtils.wrapDegrees(-350f), 1e-6);
        assertEquals(-90f, RotationUtils.wrapDegrees(270f), 1e-6);
    }

    @Test
    void deltaTakesShortestPath() {
        assertEquals(90f, RotationUtils.delta(350f, 80f), 1e-4);
        assertEquals(-90f, RotationUtils.delta(80f, 350f), 1e-4);
        assertEquals(0f, RotationUtils.delta(45f, 45f), 1e-6);
        assertEquals(180f, Math.abs(RotationUtils.delta(0f, 180f)), 1e-6);
    }

    @Test
    void lerpAngleNeverGoesTheWrongWay() {
        // From 170° to -170°: must pass through 180, not through 0
        float mid = RotationUtils.lerpAngle(170f, -170f, 0.5f);
        assertTrue(Math.abs(Math.abs(mid) - 180f) < 1f,
                "midpoint should be near ±180, got " + mid);
    }

    @Test
    void yawAndPitchMatchMinecraftConvention() {
        // +Z (south) => yaw 0; +X (east) => yaw -90 in MC convention
        assertEquals(0f, RotationUtils.yawFromDirection(0, 1), 1e-4);
        assertEquals(-90f, RotationUtils.yawFromDirection(1, 0), 1e-4);
        // Looking straight down: pitch +90
        assertEquals(90f, RotationUtils.pitchFromDirection(0, -1, 0), 1e-4);
        // Horizon: pitch 0
        assertEquals(0f, RotationUtils.pitchFromDirection(1, 0, 0), 1e-4);
    }

    @Test
    void easingEndpointsAreExact() {
        for (EasingFunctions.Mode mode : EasingFunctions.Mode.values()) {
            assertEquals(0f, EasingFunctions.apply(mode, 0f), 1e-4, mode.name());
            assertEquals(1f, EasingFunctions.apply(mode, 1f), 1e-4, mode.name());
        }
    }

    @Test
    void easingIsMonotonicForCubicFamily() {
        float prev = 0f;
        for (EasingFunctions.Mode mode : new EasingFunctions.Mode[]{
                EasingFunctions.Mode.LINEAR, EasingFunctions.Mode.EASE_IN_CUBIC,
                EasingFunctions.Mode.EASE_OUT_CUBIC, EasingFunctions.Mode.EASE_IN_OUT_CUBIC,
                EasingFunctions.Mode.SMOOTHSTEP, EasingFunctions.Mode.EASE_IN_OUT_SINE}) {
            for (float t = 0f; t <= 1f; t += 0.05f) {
                float v = EasingFunctions.apply(mode, t);
                assertTrue(v >= prev - 1e-4, mode.name() + " decreased at t=" + t);
                prev = v;
            }
            prev = 0f;
        }
    }

    @Test
    void easeOutCubicStartsFast() {
        // At t=0.25 the eased value should exceed linear
        assertTrue(EasingFunctions.apply(EasingFunctions.Mode.EASE_OUT_CUBIC, 0.25f) > 0.25f);
    }
}
