package com.donut.util;

/**
 * Shared packing of block positions into a single long
 * ({@code x:26 | z:26 | y:12}), used everywhere positions key hash maps.
 */
public final class Pos3Key {
    private Pos3Key() {
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }
}
