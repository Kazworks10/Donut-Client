package com.donut.module.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the AutoLog trigger decision: threshold + drop gating,
 * the combat-tag veto (and its laziness), and delay math.
 */
class AutoLogDeciderTest {
    private static final AutoLogDecider.BooleanSupplier NOT_TAGGED = () -> false;
    private static final AutoLogDecider.BooleanSupplier TAGGED = () -> true;

    @Test
    void triggersWhenDropCrossesThreshold() {
        AutoLogDecider.Decision d = AutoLogDecider.decide(6.0, true, 8.0, true, NOT_TAGGED, 0);
        assertTrue(d.trigger());
        assertEquals("health 6.0 < 8.0", d.reason());
        assertEquals(0, d.delayTicks());
    }

    @Test
    void noTriggerWhenAboveThreshold() {
        assertEquals(AutoLogDecider.Decision.NONE,
                AutoLogDecider.decide(15.0, true, 8.0, true, NOT_TAGGED, 0));
    }

    @Test
    void noTriggerWithoutDrop() {
        // health below threshold but stable (regen tick, not damage)
        assertEquals(AutoLogDecider.Decision.NONE,
                AutoLogDecider.decide(6.0, false, 8.0, true, NOT_TAGGED, 0));
    }

    @Test
    void combatTagVetoesHealthTrigger() {
        assertEquals(AutoLogDecider.Decision.NONE,
                AutoLogDecider.decide(6.0, true, 8.0, true, TAGGED, 0));
    }

    @Test
    void tagLookupNotConsultedWhenDisabled() {
        // respectCombatTag = false: the veto supplier must never run
        AutoLogDecider.Decision d = AutoLogDecider.decide(6.0, true, 8.0, false, () -> {
            throw new AssertionError("tag lookup must be lazy");
        }, 0);
        assertTrue(d.trigger());
    }

    @Test
    void tagLookupNotConsultedWhenNoDrop() {
        AutoLogDecider.Decision d = AutoLogDecider.decide(6.0, false, 8.0, true, () -> {
            throw new AssertionError("tag lookup must be lazy");
        }, 0);
        assertFalse(d.trigger());
    }

    @Test
    void delayTicksRoundAndClamp() {
        assertEquals(0, AutoLogDecider.delayTicks(0.0));
        assertEquals(20, AutoLogDecider.delayTicks(1.0));
        assertEquals(30, AutoLogDecider.delayTicks(1.5));
        assertEquals(200, AutoLogDecider.delayTicks(10.0));
    }
}
