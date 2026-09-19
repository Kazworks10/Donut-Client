package com.donut.gui.clickgui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnimationTest {

    @Test
    void convergesTowardTarget() {
        Animation a = new Animation(0f, 8f);
        a.set(10f);
        for (int i = 0; i < 500; i++) a.update(1f / 60f);
        assertEquals(10f, a.value(), 0.01f);
        assertTrue(a.done());
    }

    @Test
    void monotonicApproachNeverOvershoots() {
        Animation a = new Animation(0f, 8f);
        a.set(10f);
        float prev = 0f;
        for (int i = 0; i < 200; i++) {
            a.update(1f / 60f);
            assertTrue(a.value() >= prev - 1e-5);
            assertTrue(a.value() <= 10f + 1e-5);
            prev = a.value();
        }
    }

    @Test
    void snapJumpsImmediately() {
        Animation a = new Animation(0f, 8f);
        a.snap(5f);
        assertEquals(5f, a.value(), 1e-6);
        assertTrue(a.done());
    }

    @Test
    void fasterSpeedArrivesSooner() {
        Animation slow = new Animation(0f, 2f);
        Animation fast = new Animation(0f, 12f);
        slow.set(1f);
        fast.set(1f);
        for (int i = 0; i < 10; i++) {
            slow.update(1f / 60f);
            fast.update(1f / 60f);
        }
        assertTrue(fast.value() > slow.value(),
                "faster animation should be closer to target");
    }
}
