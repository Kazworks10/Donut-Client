package com.donut.schematic;

import com.donut.schematic.nbt.NbtReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Litematica .litematic files (format versions 4-6).
 * <p>
 * Reads the first region: Size, BlockStatePalette (list of {Name, Properties}),
 * BlockStates (tightly bit-packed longs, entries may straddle long boundaries).
 * Bit width is auto-detected with fallbacks to tolerate writer variations.
 */
public final class LitematicParser {
    private LitematicParser() {
    }

    public static SchematicData parse(InputStream in) throws IOException {
        NbtReader.NbtCompound root = NbtReader.open(in).readRoot();
        // Regions may be a compound (named regions, the common case) or a list.
        NbtReader.NbtCompound region = findFirstRegion(root);

        NbtReader.NbtCompound size = region.getCompound("Size");
        int sx = Math.abs(size.getInt("x", 0));
        int sy = Math.abs(size.getInt("y", 0));
        int sz = Math.abs(size.getInt("z", 0));
        if (sx == 0 || sy == 0 || sz == 0) throw new IOException("Litematic has empty size");
        long cellCount = (long) sx * sy * sz;
        if (cellCount > Integer.MAX_VALUE - 8) throw new IOException("Schematic too large: " + cellCount + " cells");

        BlockPalette palette = new BlockPalette();
        NbtReader.NbtList paletteList = region.getList("BlockStatePalette");
        List<String> localNames = new ArrayList<>(paletteList.size());
        for (int i = 0; i < paletteList.size(); i++) {
            Object entry = paletteList.get(i);
            if (entry instanceof NbtReader.NbtCompound c) {
                localNames.add(c.getString("Name", "minecraft:air"));
            } else {
                localNames.add("minecraft:air");
            }
        }
        if (localNames.isEmpty()) localNames.add("minecraft:air");

        int[] localToGlobal = new int[localNames.size()];
        for (int i = 0; i < localToGlobal.length; i++) localToGlobal[i] = palette.id(localNames.get(i));

        long[] packed = region.getLongArray("BlockStates");
        int needed = 32 - Integer.numberOfLeadingZeros(Math.max(1, localNames.size() - 1));
        short[] cells = null;
        // Tight packing is canonical, but some writers round up; try the exact
        // width first, then common fallbacks.
        for (int bits : new int[]{needed, Math.max(2, needed), Math.max(3, needed), needed + 1, needed + 2}) {
            short[] candidate = decode(packed, cellCount, bits, localNames.size());
            if (candidate != null) {
                cells = candidate;
                break;
            }
        }
        if (cells == null) throw new IOException("Could not decode BlockStates with any bit width");

        return new SchematicData(sx, sy, sz, palette, localToGlobal, cells);
    }

    /** Regions is usually a compound of named regions; falls back to a list. */
    private static NbtReader.NbtCompound findFirstRegion(NbtReader.NbtCompound root) throws IOException {
        NbtReader.NbtList list = root.getList("Regions");
        if (list.size() > 0 && list.get(0) instanceof NbtReader.NbtCompound c) {
            return c;
        }
        for (java.util.Map.Entry<String, Object> e : root.getCompound("Regions").entries()) {
            if (e.getValue() instanceof NbtReader.NbtCompound c) return c;
        }
        throw new IOException("Litematic has no regions");
    }

    /** Tightly-packed bit decode; returns null if any index lands outside the palette. */
    private static short[] decode(long[] packed, long cellCount, int bits, int paletteSize) {
        short[] cells = new short[(int) cellCount];
        long bitIndex = 0;
        long mask = (1L << bits) - 1;
        for (long i = 0; i < cellCount; i++, bitIndex += bits) {
            int longIdx = (int) (bitIndex >>> 6);
            int shift = (int) (bitIndex & 63);
            long value;
            if (longIdx >= packed.length) return null;
            if (shift + bits <= 64) {
                value = (packed[longIdx] >>> shift) & mask;
            } else {
                long low = packed[longIdx] >>> shift;
                long high = longIdx + 1 < packed.length ? packed[longIdx + 1] : 0L;
                int lowBits = 64 - shift;
                value = ((high << lowBits) | low) & mask;
            }
            if (value >= paletteSize) return null;
            cells[(int) i] = (short) value;
        }
        return cells;
    }
}
