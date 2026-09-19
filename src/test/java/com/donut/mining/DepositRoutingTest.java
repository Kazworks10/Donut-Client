package com.donut.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Headless tests for the chest-walk decision when a path search fails. */
class DepositRoutingTest {
    @Test
    void foundPathIsAlwaysFollowed() {
        assertEquals(DepositRouting.WalkDecision.FOLLOW_PATH,
                DepositRouting.decide(true, 100.0, 10.0));
        assertEquals(DepositRouting.WalkDecision.FOLLOW_PATH,
                DepositRouting.decide(true, 0.0, 10.0));
    }

    @Test
    void closeChestWithoutPathSteersStraight() {
        assertEquals(DepositRouting.WalkDecision.STEER_STRAIGHT,
                DepositRouting.decide(false, 5.0, 10.0));
    }

    @Test
    void boundaryDistanceSteersStraight() {
        assertEquals(DepositRouting.WalkDecision.STEER_STRAIGHT,
                DepositRouting.decide(false, 10.0, 10.0));
    }

    @Test
    void farChestWithoutPathGivesUp() {
        assertEquals(DepositRouting.WalkDecision.GIVE_UP,
                DepositRouting.decide(false, 10.5, 10.0));
    }
}
