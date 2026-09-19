package com.donut.pathfinding;

/**
 * Immutable description of a block relevant to pathfinding.
 * Pure data: no Minecraft types, so the A* core runs headless in tests.
 */
public final class BlockProps {
    public static final BlockProps AIR = new BlockProps(false, false, false, false, 0f, true);
    public static final BlockProps SOLID = new BlockProps(true, false, false, false, 0f, false);
    public static final BlockProps WATER = new BlockProps(false, true, false, false, 1.0f, false);
    public static final BlockProps LAVA = new BlockProps(false, true, false, true, 12.0f, false);
    public static final BlockProps LADDER = new BlockProps(false, false, true, false, 0f, false);
    public static final BlockProps CACTUS = new BlockProps(true, false, false, true, 5.0f, false);
    public static final BlockProps FIRE = new BlockProps(false, false, false, true, 6.0f, false);
    public static final BlockProps LEAVES = new BlockProps(false, false, false, false, 0.4f, true);

    /** Blocks movement (full collision). */
    public final boolean solid;
    /** Liquid (water, lava). */
    public final boolean liquid;
    /** Climbable (ladder, vine). */
    public final boolean climbable;
    /** Deals damage or otherwise hostile to walk on/through. */
    public final boolean dangerous;
    /** Extra movement cost multiplier contributed when adjacent/inside (lava high, cactus medium). */
    public final float dangerWeight;
    /** Can be walked through without breaking (air, leaves, grass...). */
    public final boolean passable;

    public BlockProps(boolean solid, boolean liquid, boolean climbable, boolean dangerous, float dangerWeight, boolean passable) {
        this.solid = solid;
        this.liquid = liquid;
        this.climbable = climbable;
        this.dangerous = dangerous;
        this.dangerWeight = dangerWeight;
        this.passable = passable;
    }

    public boolean isPassable() {
        return passable && !solid;
    }
}
