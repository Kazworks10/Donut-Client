package com.donut.module.modules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the AutoTool selection policy: best usable tool wins,
 * only on strict improvement, ties stay on the current slot.
 */
class AutoToolSelectorTest {
    private AutoToolSelector.Scores scores(double... bySlot) {
        return new AutoToolSelector.Scores(List.of(box(bySlot)));
    }

    private static Double[] box(double... v) {
        Double[] out = new Double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i];
        return out;
    }

    @Test
    void bestEligibleSlotWins() {
        // slots: 0 fist(-1) 1 iron(6) 2 diamond(8) 3 netherite(9)
        assertEquals(3, AutoToolSelector.select(
                scores(-1, 6, 8, 9, -1, -1, -1, -1, -1), 0));
    }

    @Test
    void ineligibleScoresNeverWin() {
        // only slot 2 holds a non-tool (-1): nothing better than fist -> no swap
        assertEquals(-1, AutoToolSelector.select(
                scores(-1, -1, -1, -1, -1, -1, -1, -1, -1), 0));
    }

    @Test
    void strictImprovementRequired() {
        // current slot holds the equal-best tool: no swap
        assertEquals(-1, AutoToolSelector.select(
                scores(-1, 8, 8, -1, -1, -1, -1, -1, -1), 1));
    }

    @Test
    void tiePrefersLowerSlotWhenSwapping() {
        // current = 4 (worse), slots 2 and 6 tie at 8 -> pick 2
        assertEquals(2, AutoToolSelector.select(
                scores(-1, -1, 8, -1, 2, -1, 8, -1, -1), 4));
    }

    @Test
    void alreadyOnBestNeverSwaps() {
        assertEquals(-1, AutoToolSelector.select(
                scores(-1, 3, 9, 5, -1, -1, -1, -1, -1), 2));
    }

    @Test
    void rejectsWrongSize() {
        assertTrue(AssertUtil.throwsIllegalArgument(() ->
                new AutoToolSelector.Scores(List.of(1.0, 2.0))));
    }

    private static final class AssertUtil {
        static boolean throwsIllegalArgument(Runnable r) {
            try {
                r.run();
                return false;
            } catch (IllegalArgumentException e) {
                return true;
            }
        }
    }
}
