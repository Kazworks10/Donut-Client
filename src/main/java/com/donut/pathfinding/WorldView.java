package com.donut.pathfinding;

/**
 * Read-only view of the world for the pathfinding graph. Implemented by a thin
 * Minecraft adapter so the search itself is pure and unit-testable. Only loaded
 * chunks are queried; {@link #isLoaded} must return false for anything else.
 */
public interface WorldView {
    /** Properties of the block at integer coordinates. Unloaded chunks return null. */
    BlockProps block(int x, int y, int z);

    /** Whether the chunk containing (x, z) is loaded and readable. */
    boolean isLoaded(int x, int z);

    /** Lowest buildable Y in this world. */
    int minY();

    /** Highest buildable Y in this world. */
    int maxY();
}
