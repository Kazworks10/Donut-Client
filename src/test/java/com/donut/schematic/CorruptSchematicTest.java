package com.donut.schematic;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Corrupt and hostile schematic files must fail with a descriptive
 * {@link java.io.IOException} — never NPE, StackOverflowError, or a bare
 * message-less EOF that surfaces as "load failed: java.io.EOFException" in
 * the GUI. Covers the defects observed in playtesting.
 */
class CorruptSchematicTest {
    private static byte[] gzip(byte[] raw) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(raw);
        }
        return bos.toByteArray();
    }

    private static Path write(String name, byte[] content) throws Exception {
        Path f = Files.createTempFile("donut-corrupt-", name);
        Files.write(f, content);
        return f;
    }

    private static void assertFailsCleanly(String label, byte[] content, String name) throws Exception {
        Path f = write(name, content);
        try {
            IOException e = assertThrows(IOException.class, () -> SchematicFormat.parse(f),
                    label + " must fail with IOException");
            assertNotNull(e.getMessage(), label + " exception must carry a message");
            assertTrue(!e.getMessage().isBlank(), label + " exception message must not be blank");
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void garbageBytesFailWithMessageNotNpe() throws Exception {
        byte[] garbage = new byte[512];
        new Random(42).nextBytes(garbage);
        assertFailsCleanly("random bytes as .litematic", garbage, "a.litematic");
        assertFailsCleanly("random bytes as .schem", garbage, "b.schem");
        assertFailsCleanly("random bytes with unknown extension", garbage, "c.bin");
        assertFailsCleanly("gzip-wrapped garbage", gzip(garbage), "d.litematic");
    }

    @Test
    void truncatedNbtFailsWithDescriptiveMessage() throws Exception {
        byte[] truncated = {0x0A, 0x00, 0x00, 0x03, 0x00, 0x04, 'n', 'a', 'm', 'e', 0x01};
        Path f = write("truncated.litematic", gzip(truncated));
        IOException e = assertThrows(IOException.class, () -> SchematicFormat.parse(f));
        assertNotNull(e.getMessage());
        assertTrue(e.getMessage().toLowerCase().contains("end of file"),
                "message must explain the truncation, got: " + e.getMessage());
        Files.deleteIfExists(f);
    }

    @Test
    void deepNestingFailsSafelyInsteadOfStackOverflow() throws Exception {
        ByteArrayOutputStream deep = new ByteArrayOutputStream();
        for (int i = 0; i < 5000; i++) deep.write(new byte[]{0x0A, 0x00, 0x00});
        assertFailsCleanly("5000-deep compound nesting", gzip(deep.toByteArray()), "deep.litematic");
    }
}
