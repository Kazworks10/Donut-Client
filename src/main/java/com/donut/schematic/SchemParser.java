package com.donut.schematic;

import com.donut.schematic.nbt.NbtReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Sponge Schematic v2/v3 (.schem):
 * root { Width, Height, Length (shorts), Palette: {name -> id}, BlockData
 * (varint byte array, YZX order), Offset?, Version, DataVersion }.
 */
public final class SchemParser {
    private SchemParser() {
    }

    public static SchematicData parse(InputStream in) throws IOException {
        NbtReader.NbtCompound root = NbtReader.open(in).readRoot();

        int sx = Short.toUnsignedInt((short) root.getInt("Width", 0));
        int sy = Short.toUnsignedInt((short) root.getInt("Height", 0));
        int sz = Short.toUnsignedInt((short) root.getInt("Length", 0));
        if (sx == 0 || sy == 0 || sz == 0) throw new IOException("Sponge schematic has empty size");
        long cellCount = (long) sx * sy * sz;
        if (cellCount > Integer.MAX_VALUE - 8) throw new IOException("Schematic too large: " + cellCount + " cells");

        BlockPalette palette = new BlockPalette();
        NbtReader.NbtCompound paletteRoot = root.getCompound("Palette");
        int maxId = 0;
        List<int[]> idEntries = new ArrayList<>();
        for (java.util.Map.Entry<String, Object> e : paletteRoot.entries()) {
            int id = e.getValue() instanceof Number n ? n.intValue() : 0;
            idEntries.add(new int[]{id, palette.id(stripState(e.getKey()))});
            if (id > maxId) maxId = id;
        }
        int[] localToGlobal = new int[maxId + 1];
        for (int[] entry : idEntries) localToGlobal[entry[0]] = entry[1];

        byte[] data = root.getByteArray("BlockData");
        short[] cells = new short[(int) cellCount];
        int i = 0;
        for (int pos = 0; pos < data.length && i < cellCount; ) {
            int value = 0, shift = 0, b;
            do {
                if (pos >= data.length) throw new IOException("Truncated varint BlockData");
                b = data[pos++] & 0xFF;
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            if (value > maxId) throw new IOException("Block id " + value + " outside palette");
            cells[i++] = (short) value;
        }

        return new SchematicData(sx, sy, sz, palette, localToGlobal, cells);
    }

    /** Strips blockstate properties: "minecraft:oak_log[axis=y]" -> "minecraft:oak_log". */
    static String stripState(String key) {
        int bracket = key.indexOf('[');
        return bracket >= 0 ? key.substring(0, bracket) : key;
    }
}
