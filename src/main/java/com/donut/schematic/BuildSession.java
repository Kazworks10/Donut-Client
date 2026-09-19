package com.donut.schematic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent build progress so large builds survive disconnects and restarts.
 * The session file stores the schematic path, origin, mode and the placement
 * index; resuming seeks the planner to the same ordering and continues.
 */
public final class BuildSession {
    public final String schematicPath;
    public final int originX, originY, originZ;
    public final String mode;
    public int index;

    public BuildSession(String schematicPath, int originX, int originY, int originZ, String mode, int index) {
        this.schematicPath = schematicPath;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.mode = mode;
        this.index = index;
    }

    private static final String SEP = "|";

    /** Saves under {@code <schematics-dir>/.progress/<hash>.session}. Returns the file path, or null on failure. */
    public Path save(Path schematicsDir) {
        try {
            Path dir = schematicsDir.resolve(".progress");
            Files.createDirectories(dir);
            String line = String.join(SEP,
                    schematicPath,
                    String.valueOf(originX), String.valueOf(originY), String.valueOf(originZ),
                    mode == null ? "layer" : mode,
                    String.valueOf(index));
            Path file = dir.resolve(sessionName());
            // Back up the previous snapshot before overwriting (crash safety).
            if (Files.exists(file)) {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(file, line, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            System.err.println("[Donut] Failed to save build session: " + e);
            return null;
        }
    }

    /** Loads the most recent session for this schematic, or null. */
    public static BuildSession load(Path schematicsDir, String schematicPath) {
        return load(schematicsDir, schematicPath, 0, 0, 0);
    }

    /**
     * Loads the session saved for this schematic + origin (origin-only lookup
     * kept for callers that do not know the origin). Returns null if none.
     */
    public static BuildSession load(Path schematicsDir, String schematicPath, int ox, int oy, int oz) {
        Path file = schematicsDir.resolve(".progress").resolve(sessionName(schematicPath, ox, oy, oz));
        if (!Files.exists(file)) return null;
        try {
            String line = Files.readString(file, StandardCharsets.UTF_8).trim();
            String[] p = line.split("\\Q" + SEP + "\\E");
            if (p.length < 6) return null;
            return new BuildSession(p[0],
                    Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                    p[4], Integer.parseInt(p[5]));
        } catch (IOException | NumberFormatException e) {
            System.err.println("[Donut] Failed to read build session: " + e);
            return null;
        }
    }

    /** Deletes the saved session for this schematic + origin, if any. */
    public static boolean delete(Path schematicsDir, String schematicPath, int ox, int oy, int oz) {
        try {
            return Files.deleteIfExists(schematicsDir.resolve(".progress")
                    .resolve(sessionName(schematicPath, ox, oy, oz)));
        } catch (IOException e) {
            return false;
        }
    }

    private String sessionName() {
        return sessionName(schematicPath, originX, originY, originZ);
    }

    private static String sessionName(String schematicPath, int x, int y, int z) {
        return Integer.toHexString(schematicPath.hashCode())
                + "-" + x + "_" + y + "_" + z + ".session";
    }

    /** Lists saved sessions (for a future resume UI). */
    public static List<Path> listSessions(Path schematicsDir) {
        Path dir = schematicsDir.resolve(".progress");
        if (!Files.isDirectory(dir)) return List.of();
        List<Path> out = new ArrayList<>();
        try (var s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".session")).forEach(out::add);
        } catch (IOException ignored) {
        }
        return out;
    }
}
