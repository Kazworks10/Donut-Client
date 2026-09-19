package com.donut.schematic;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Builds tiny .litematic and .schem files byte-by-byte and round-trips them
 * through the parsers. These are format-conformance tests: they pin the NBT
 * layout, bit packing and varint encoding the parsers must handle.
 */
class SchematicParserTest {

    // ---- Litematic ---------------------------------------------------------

    @Test
    void litematicCompoundRegionsTightPacking() throws IOException {
        // 2x1x2, palette [air, stone, oak_planks], 2 bits per entry (tight)
        List<String> palette = List.of("minecraft:air", "minecraft:stone", "minecraft:oak_planks");
        int[] cells = {1, 2, 1, 0}; // YZX order
        byte[] bytes = litematic(2, 1, 2, palette, 2, cells, true);

        SchematicData data = LitematicParser.parse(new ByteArrayInputStream(bytes));

        assertEquals(2, data.sizeX());
        assertEquals(1, data.sizeY());
        assertEquals(2, data.sizeZ());
        assertEquals(3, data.nonAirCount());
        assertEquals("minecraft:stone", data.blockName(0, 0, 0));
        assertEquals("minecraft:oak_planks", data.blockName(1, 0, 0));
        assertEquals("minecraft:stone", data.blockName(0, 0, 1));
        assertNull(data.blockName(1, 0, 1));
    }

    @Test
    void litematicListRegionsAndTightBits() throws IOException {
        // Regions written as a LIST; 3 palette entries tightly packed at 2 bits.
        // (A rounded-up width is inherently ambiguous and cannot be asserted.)
        List<String> palette = List.of("minecraft:air", "minecraft:stone", "minecraft:dirt");
        int[] cells = {1, 2, 2};
        byte[] bytes = litematic(3, 1, 1, palette, 2, cells, false);

        SchematicData data = LitematicParser.parse(new ByteArrayInputStream(bytes));
        assertEquals("minecraft:stone", data.blockName(0, 0, 0));
        assertEquals("minecraft:dirt", data.blockName(1, 0, 0));
        assertEquals("minecraft:dirt", data.blockName(2, 0, 0));
        assertEquals(3, data.nonAirCount());
    }

    // ---- Sponge ------------------------------------------------------------

    @Test
    void spongeV2VarintBlockData() throws IOException {
        // 2x2x1; palette: stone=0, oak_planks=1, air=2 (Sponge assigns air explicitly)
        var palette = new java.util.LinkedHashMap<String, Integer>();
        palette.put("minecraft:stone", 0);
        palette.put("minecraft:oak_planks", 1);
        palette.put("minecraft:air", 2);
        int[] cells = {0, 1, 1, 2}; // YZX
        byte[] bytes = sponge(palette, cells, 2, 2, 1);

        SchematicData data = SchemParser.parse(new ByteArrayInputStream(bytes));
        assertEquals(2, data.sizeX());
        assertEquals(2, data.sizeY());
        assertEquals(1, data.sizeZ());
        assertEquals("minecraft:stone", data.blockName(0, 0, 0));
        assertEquals("minecraft:oak_planks", data.blockName(1, 0, 0));
        assertEquals("minecraft:oak_planks", data.blockName(0, 1, 0));
        assertNull(data.blockName(1, 1, 0));
    }

    @Test
    void spongeStripsBlockStateProperties() throws IOException {
        var palette = new java.util.LinkedHashMap<String, Integer>();
        palette.put("minecraft:oak_log[axis=y]", 0);
        byte[] bytes = sponge(palette, new int[]{0}, 1, 1, 1);
        SchematicData data = SchemParser.parse(new ByteArrayInputStream(bytes));
        assertEquals("minecraft:oak_log", data.blockName(0, 0, 0));
    }

    // ---- PlacementPlanner ---------------------------------------------------

    @Test
    void layerOrderingIsYThenZThenX() {
        // 2x2x2 where every cell is a distinct stone id, all non-air
        List<String> palette = List.of("minecraft:air", "minecraft:stone");
        SchematicData data = inlineSchematic(2, 2, 2, palette, fillCells(8, (short) 1));

        var plan = PlacementPlanner.plan(data, "layer", 0, 0);
        assertEquals(8, plan.size());
        assertEquals(0, plan.get(0)[1]); // lowest layer first
        assertEquals(0, plan.get(0)[0]);
        assertEquals(0, plan.get(0)[2]);
        assertEquals(1, plan.get(4)[1]); // 2x2 footprint, so layer 2 starts at index 4
    }

    @Test
    void nearestOrderingStartsClosest() {
        SchematicData data = inlineSchematic(2, 1, 1,
                List.of("minecraft:air", "minecraft:stone"), new short[]{1, 1});
        // origin x=1: cell (1,0,0) is distance 0, (0,0,0) is distance 1
        var plan = PlacementPlanner.plan(data, "nearest", 1, 0);
        assertEquals(1, plan.get(0)[0]);
    }

    @Test
    void supportPassPlacesAttachableAboveAfterItsSupport() {
        // column: y0 stone, y1 torch -> torch must come right after the stone
        List<String> palette = List.of("minecraft:air", "minecraft:stone", "minecraft:torch");
        SchematicData data = inlineSchematic(1, 2, 1, palette, new short[]{1, 2});
        var plan = PlacementPlanner.plan(data, "layer", 0, 0);
        assertEquals(2, plan.size());
        assertEquals(0, plan.get(0)[1]); // stone first (y=0)
        assertEquals(1, plan.get(1)[1]); // torch directly after (y=1)
        assertEquals(2, plan.get(1)[3]); // torch id
    }

    // ---- BuildSession --------------------------------------------------------

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path tempDir;

    @Test
    void buildSessionRoundTrip() {
        BuildSession session = new BuildSession("test.litematic", 0, 0, 0, "layer", 1234);
        assertNotNull(session.save(tempDir));
        BuildSession loaded = BuildSession.load(tempDir, "test.litematic");
        assertNotNull(loaded);
        assertEquals(1234, loaded.index);
        assertEquals("layer", loaded.mode);
        assertEquals("test.litematic", loaded.schematicPath);
    }

    @Test
    void buildSessionLoadMissingReturnsNull() {
        assertNull(BuildSession.load(tempDir, "never-saved.litematic"));
    }

    // ---- helpers --------------------------------------------------------------

    private SchematicData inlineSchematic(int sx, int sy, int sz, List<String> names, short[] cells) {
        BlockPalette palette = new BlockPalette();
        int[] localToGlobal = new int[names.size()];
        for (int i = 0; i < names.size(); i++) localToGlobal[i] = palette.id(names.get(i));
        return new SchematicData(sx, sy, sz, palette, localToGlobal, cells);
    }

    private static short[] fillCells(int n, short id) {
        short[] out = new short[n];
        java.util.Arrays.fill(out, id);
        return out;
    }

    /** Writes a minimal litematic. regionsAsCompound=true uses the named-compound form. */
    static byte[] litematic(int sx, int sy, int sz, List<String> palette, int bits,
                            int[] cells, boolean regionsAsCompound) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(new GZIPOutputStream(bout, true));

        d.writeByte(10);
        d.writeUTF(""); // root compound
        {
            d.writeByte(10);
            d.writeUTF("Metadata");
            d.writeByte(0);
            if (regionsAsCompound) {
                d.writeByte(10);
                d.writeUTF("Regions");
                d.writeByte(10);
                d.writeUTF("main");
                writeRegionBody(d, sx, sy, sz, palette, bits, cells); // ends with TAG_END
                d.writeByte(0); // end Regions compound
            } else {
                d.writeByte(9);
                d.writeUTF("Regions"); // Regions value IS a list
                d.writeByte(10);
                d.writeInt(1);
                writeRegionBody(d, sx, sy, sz, palette, bits, cells); // bare compound payload
                // No extra terminator: lists carry their length.
            }
            d.writeByte(0); // end root
        }
        d.close();
        return bout.toByteArray();
    }

    private static void writeRegionBody(DataOutputStream d, int sx, int sy, int sz,
                                        List<String> palette, int bits, int[] cells) throws IOException {
        d.writeByte(10);
        d.writeUTF("Size");
        d.writeByte(3);
        d.writeUTF("x");
        d.writeInt(sx);
        d.writeByte(3);
        d.writeUTF("y");
        d.writeInt(sy);
        d.writeByte(3);
        d.writeUTF("z");
        d.writeInt(sz);
        d.writeByte(0);

        d.writeByte(9);
        d.writeUTF("BlockStatePalette");
        d.writeByte(10);
        d.writeInt(palette.size());
        for (String name : palette) {
            // List entries are BARE payloads: a compound body with its end tag,
            // no type byte and no name (those live in the list header).
            d.writeByte(8);
            d.writeUTF("Name");
            d.writeUTF(name);
            d.writeByte(0);
        }

        // Tight bit packing, entries may straddle long boundaries
        long totalBits = (long) bits * cells.length;
        long[] packed = new long[(int) ((totalBits + 63) / 64)];
        long bitIndex = 0;
        for (int cell : cells) {
            int longIdx = (int) (bitIndex >>> 6);
            int shift = (int) (bitIndex & 63);
            packed[longIdx] |= ((long) cell & 0xFFFFFFFFL) << shift;
            int overflow = shift + bits - 64;
            if (overflow > 0 && longIdx + 1 < packed.length) {
                packed[longIdx + 1] |= ((long) cell) >>> (bits - overflow);
            }
            bitIndex += bits;
        }
        d.writeByte(12);
        d.writeUTF("BlockStates");
        d.writeInt(packed.length);
        for (long v : packed) d.writeLong(v);
        d.writeByte(0); // end region compound
    }

    /** Writes a minimal Sponge v2 .schem with varint BlockData. */
    private static byte[] sponge(java.util.Map<String, Integer> palette, int[] cells,
                                 int sx, int sy, int sz) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(new GZIPOutputStream(bout, true));
        d.writeByte(10);
        d.writeUTF("Schematic");
        {
            d.writeByte(2);
            d.writeUTF("Width");
            d.writeShort(sx);
            d.writeByte(2);
            d.writeUTF("Height");
            d.writeShort(sy);
            d.writeByte(2);
            d.writeUTF("Length");
            d.writeShort(sz);
            d.writeByte(3);
            d.writeUTF("Version");
            d.writeInt(2);
            d.writeByte(3);
            d.writeUTF("DataVersion");
            d.writeInt(3953);

            d.writeByte(10);
            d.writeUTF("Palette");
            for (var e : palette.entrySet()) {
                d.writeByte(3);
                d.writeUTF(e.getKey());
                d.writeInt(e.getValue());
            }
            d.writeByte(0);

            ByteArrayOutputStream varint = new ByteArrayOutputStream();
            for (int cell : cells) {
                int v = cell;
                while ((v & ~0x7F) != 0) {
                    varint.write((v & 0x7F) | 0x80);
                    v >>>= 7;
                }
                varint.write(v);
            }
            d.writeByte(7);
            d.writeUTF("BlockData");
            d.writeInt(varint.size());
            d.write(varint.toByteArray());
            d.writeByte(0); // end root
        }
        d.close();
        return bout.toByteArray();
    }
}
