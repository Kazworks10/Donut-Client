package com.donut.pathfinding;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches per-column danger samples (0..1) keyed by chunk + revision so repeated
 * searches over stable terrain don't rescan blocks. Entries are dropped when the
 * underlying chunk revision changes.
 */
public final class DangerMap {
    private record ChunkKey(long packed, int revision) {
    }

    private final WorldView world;
    private final Map<Long, ChunkKey> revisions = new ConcurrentHashMap<>();
    private final Map<Long, float[]> columns = new ConcurrentHashMap<>();

    public DangerMap(WorldView world) {
        this.world = world;
    }

    /** Danger of standing at (x, z): fraction of hostile columns in the surrounding 3x3. Cheap. */
    public float sample(int x, int z) {
        long ck = chunkKey(x >> 4, z >> 4);
        int rev = worldRevision(x >> 4, z >> 4);
        ChunkKey key = new ChunkKey(ck, rev);
        ChunkKey prev = revisions.put(ck, key);
        if (prev == null || prev.revision() != rev) columns.remove(ck);

        float[] grid = columns.computeIfAbsent(ck, k -> buildGrid(x >> 4, z >> 4));
        int lx = (x & 15), lz = (z & 15);
        return grid[lz * 16 + lx];
    }

    private int worldRevision(int cx, int cz) {
        // WorldView exposes no revision; treat unloaded as -1 and loaded as stable 0.
        // Chunk-change invalidation is handled at the executor level (replan on block updates).
        return world.isLoaded(cx << 4, cz << 4) ? 0 : -1;
    }

    private float[] buildGrid(int cx, int cz) {
        float[] grid = new float[256];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                grid[lz * 16 + lx] = columnDanger((cx << 4) + lx, (cz << 4) + lz);
            }
        }
        return grid;
    }

    private float columnDanger(int x, int z) {
        float danger = 0f;
        int lo = Math.max(world.minY(), world.minY());
        int hi = world.maxY();
        for (int y = lo; y <= hi && danger < 1f; y++) {
            BlockProps b = world.block(x, y, z);
            if (b != null && b.dangerous) {
                danger = Math.min(1f, danger + Math.max(0.3f, b.dangerWeight / 12f));
                break; // one hazard column is enough to flag the cell
            }
        }
        return danger;
    }

    private static long chunkKey(int cx, int cz) {
        return (long) cx << 32 | cz & 0xFFFFFFFFL;
    }
}
