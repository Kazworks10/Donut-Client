package com.donut.pathfinding;

/**
 * Heuristic for the block-level A*: octile distance on XZ plus vertical cost.
 * Slightly optimistic relative to movement costs so A* stays fast but complete.
 */
public final class Heuristic {
    private Heuristic() {
    }

    public static float octile(int ax, int az, int bx, int bz) {
        int dx = Math.abs(ax - bx);
        int dz = Math.abs(az - bz);
        int min = Math.min(dx, dz);
        int max = Math.max(dx, dz);
        return max - min + 1.41421356f * min;
    }

    public static float cost(int ax, int ay, int az, int bx, int by, int bz) {
        float horizontal = octile(ax, az, bx, bz);
        float vertical = Math.abs(ay - by) * 1.1f;
        return horizontal + vertical;
    }
}
