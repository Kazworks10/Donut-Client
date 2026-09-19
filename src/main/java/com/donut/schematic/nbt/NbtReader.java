package com.donut.schematic.nbt;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimal self-contained NBT binary reader (big-endian, named compounds).
 * Supports all 13 tag types; arrays are read into byte/int arrays, strings as
 * modified UTF-8. Streams from GZip or raw input. No Minecraft dependencies,
 * so parsers are unit-testable headless.
 */
public final class NbtReader {
    public static final byte TAG_END = 0, TAG_BYTE = 1, TAG_SHORT = 2, TAG_INT = 3, TAG_LONG = 4,
            TAG_FLOAT = 5, TAG_DOUBLE = 6, TAG_BYTE_ARRAY = 7, TAG_STRING = 8, TAG_LIST = 9,
            TAG_COMPOUND = 10, TAG_INT_ARRAY = 11, TAG_LONG_ARRAY = 12;

    private final DataInputStream in;

    private NbtReader(DataInputStream in) {
        this.in = in;
    }

    /** Opens an NBT stream, auto-detecting GZip via magic bytes. */
    public static NbtReader open(InputStream raw) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(raw, 8192);
        buffered.mark(2);
        int b0 = buffered.read();
        int b1 = buffered.read();
        buffered.reset();
        InputStream stream = (b0 == 0x1F && b1 == 0x8B) ? new GZIPInputStream(buffered, 8192) : buffered;
        return new NbtReader(new DataInputStream(stream));
    }

    /** Reads the root compound (with its name) and closes the stream. */
    public NbtCompound readRoot() throws IOException {
        int type = in.readUnsignedByte();
        if (type != TAG_COMPOUND) throw new IOException("Root tag is not a compound: " + type);
        String name = readString();
        NbtCompound root = readCompoundBody();
        in.close();
        return root;
    }

    private NbtCompound readCompoundBody() throws IOException {
        NbtCompound compound = new NbtCompound();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == TAG_END) return compound;
            String key = readString();
            compound.put(key, readPayload(type));
        }
    }

    private Object readPayload(int type) throws IOException {
        return switch (type) {
            case TAG_BYTE -> in.readByte();
            case TAG_SHORT -> in.readShort();
            case TAG_INT -> in.readInt();
            case TAG_LONG -> in.readLong();
            case TAG_FLOAT -> in.readFloat();
            case TAG_DOUBLE -> in.readDouble();
            case TAG_BYTE_ARRAY -> readByteArray();
            case TAG_STRING -> readString();
            case TAG_LIST -> readList();
            case TAG_COMPOUND -> readCompoundBody();
            case TAG_INT_ARRAY -> readIntArray();
            case TAG_LONG_ARRAY -> readLongArray();
            default -> throw new IOException("Unknown NBT tag type: " + type);
        };
    }

    private String readString() throws IOException {
        int len = in.readUnsignedShort();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] readByteArray() throws IOException {
        int len = in.readInt();
        if (len < 0) throw new IOException("Negative byte array length: " + len);
        byte[] out = new byte[len];
        in.readFully(out);
        return out;
    }

    private int[] readIntArray() throws IOException {
        int len = in.readInt();
        if (len < 0) throw new IOException("Negative int array length: " + len);
        int[] out = new int[len];
        for (int i = 0; i < len; i++) out[i] = in.readInt();
        return out;
    }

    private long[] readLongArray() throws IOException {
        int len = in.readInt();
        if (len < 0) throw new IOException("Negative long array length: " + len);
        long[] out = new long[len];
        for (int i = 0; i < len; i++) out[i] = in.readLong();
        return out;
    }

    private NbtList readList() throws IOException {
        int elementType = in.readUnsignedByte();
        int len = in.readInt();
        if (len < 0) throw new IOException("Negative list length: " + len);
        List<Object> values = new ArrayList<>(Math.min(len, 4096));
        for (int i = 0; i < len; i++) values.add(readPayload(elementType));
        return new NbtList(elementType, values);
    }

    // ---- value wrappers ----------------------------------------------------

    public static final class NbtCompound {
        private final Map<String, Object> values = new HashMap<>();

        void put(String key, Object value) {
            values.put(key, value);
        }

        public NbtCompound getCompound(String key) {
            Object v = values.get(key);
            return v instanceof NbtCompound c ? c : new NbtCompound();
        }

        public NbtList getList(String key) {
            Object v = values.get(key);
            return v instanceof NbtList l ? l : new NbtList(TAG_END, List.of());
        }

        public String getString(String key, String def) {
            Object v = values.get(key);
            return v instanceof String s ? s : def;
        }

        public int getInt(String key, int def) {
            Object v = values.get(key);
            if (v instanceof Byte b) return b.intValue();
            if (v instanceof Short s) return s.intValue();
            if (v instanceof Integer i) return i;
            if (v instanceof Long l) return l.intValue();
            return def;
        }

        public long getLong(String key, long def) {
            Object v = values.get(key);
            if (v instanceof Long l) return l;
            if (v instanceof Integer i) return i.longValue();
            if (v instanceof Byte b) return b.longValue();
            if (v instanceof Short s) return s.longValue();
            return def;
        }

        public byte[] getByteArray(String key) {
            Object v = values.get(key);
            return v instanceof byte[] b ? b : new byte[0];
        }

        public int[] getIntArray(String key) {
            Object v = values.get(key);
            return v instanceof int[] a ? a : new int[0];
        }

        public long[] getLongArray(String key) {
            Object v = values.get(key);
            return v instanceof long[] a ? a : new long[0];
        }

        public boolean contains(String key) {
            return values.containsKey(key);
        }

        /** Iterates all entries; needed for palette compounds (index/name pairs). */
        public Iterable<Map.Entry<String, Object>> entries() {
            return values.entrySet();
        }
    }

    public record NbtList(int elementType, List<Object> values) {
        public int size() {
            return values.size();
        }

        public Object get(int index) {
            return values.get(index);
        }
    }
}
