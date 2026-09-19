package com.donut.schematic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Format enum and auto-detecting entry point for schematic files. */
public enum SchematicFormat {
    LITEMATIC, SCHEM;

    public static SchematicData parse(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase();
        try (InputStream in = Files.newInputStream(file)) {
            if (name.endsWith(".litematic")) return LitematicParser.parse(in);
            if (name.endsWith(".schem")) return SchemParser.parse(in);
            if (name.endsWith(".schematic")) throw new IOException("Legacy MCEdit .schematic is not supported yet");
            // Extension-agnostic: sniff for GZip + first string tag
            return sniff(in);
        }
    }

    private static SchematicData sniff(InputStream in) throws IOException {
        in.mark(2);
        int b0 = in.read();
        int b1 = in.read();
        in.reset();
        if (b0 == 0x0A) return SchemParser.parse(in); // raw NBT compound starts with TAG_COMPOUND
        return LitematicParser.parse(in); // gzip'd litematic is the common default
    }

    public static SchematicData load(Path file) throws IOException {
        return parse(file);
    }
}
