package com.donut.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Headless tests for the shared 3D position packing. */
class Pos3KeyTest {
    @Test
    void distinctPositionsPackDistinctly() {
        long a = Pos3Key.pack(1, 2, 3);
        long b = Pos3Key.pack(3, 2, 1);
        assertNotEquals(a, b);
        assertNotEquals(a, Pos3Key.pack(-1, 2, 3));
    }

    @Test
    void packingIsUniqueAcrossAxisSwaps() {
        // 26-bit x/z and 12-bit y must not collide for realistic coords
        for (int d = 1; d <= 4096; d <<= 1) {
            assertNotEquals(Pos3Key.pack(d, 0, 0), Pos3Key.pack(0, 0, d));
            assertNotEquals(Pos3Key.pack(0, d, 0), Pos3Key.pack(0, 0, d));
            assertNotEquals(Pos3Key.pack(d, 0, 0), Pos3Key.pack(0, d, 0));
        }
    }

    @Test
    void packIsStable() {
        assertEquals(Pos3Key.pack(12345, -67, 8901), Pos3Key.pack(12345, -67, 8901));
    }
}
