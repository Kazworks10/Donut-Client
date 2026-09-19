package com.donut.schematic;

/**
 * A parsed schematic: dimensions plus a compact per-cell local-palette index
 * array (YZX order). Block names decode lazily through the shared palette, so
 * even 10M+ block schematics stay at roughly 2 bytes per cell.
 */
public final class SchematicData {
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final BlockPalette palette;
    private final int[] localToGlobal;
    private final short[] cells; // local palette indices, 0 = air
    private long nonAirCount;

    public SchematicData(int sizeX, int sizeY, int sizeZ, BlockPalette palette, int[] localToGlobal, short[] cells) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = palette;
        this.localToGlobal = localToGlobal;
        this.cells = cells;
        for (short c : cells) {
            if (c != 0) nonAirCount++;
        }
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public long nonAirCount() {
        return nonAirCount;
    }

    private int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    /** Raw local palette index at a cell (0 = air). */
    public short localIndex(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) return 0;
        return cells[index(x, y, z)];
    }

    /** Global palette id at a cell (0 = air). */
    public int globalId(int x, int y, int z) {
        short local = localIndex(x, y, z);
        return local < localToGlobal.length ? localToGlobal[local] : 0;
    }

    /** Block name at a cell, or null for air/empty. */
    public String blockName(int x, int y, int z) {
        int global = globalId(x, y, z);
        return palette.isAir(global) ? null : palette.name(global);
    }

    public BlockPalette palette() {
        return palette;
    }

    @FunctionalInterface
    public interface Visitor {
        void accept(int x, int y, int z, int globalId);
    }

    /** Iterates every non-air cell in YZX order without materializing a block list. */
    public void forEachNonAir(Visitor visitor) {
        int i = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++, i++) {
                    short local = cells[i];
                    if (local == 0) continue;
                    int global = local < localToGlobal.length ? localToGlobal[local] : 0;
                    if (palette.isAir(global)) continue;
                    visitor.accept(x, y, z, global);
                }
            }
        }
    }
}
