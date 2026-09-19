package com.donut.pathfinding;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Two-phase hierarchical pathfinder:
 * <ol>
 *   <li>Chunk-level BFS over a walkability graph to find a corridor of chunks;</li>
 *   <li>Block-level A* inside that corridor (one segment per chunk).</li>
 * </ol>
 * The block search is optionally smoothed afterward via {@link PathSmoother}.
 */
public final class HierarchicalAStar {
    private HierarchicalAStar() {
    }

    /**
     * Finds a path asynchronously. Returns a future completing with a success
     * result (waypoints in block space) or a failure reason.
     */
    public static CompletableFuture<PathResult> findPath(WorldView world, int sx, int sy, int sz,
                                                         int gx, int gy, int gz, PathOptions opts) {
        Executor exec = opts.executor;
        return CompletableFuture.supplyAsync(() -> compute(world, sx, sy, sz, gx, gy, gz, opts), exec);
    }

    static PathResult compute(WorldView world, int sx, int sy, int sz, int gx, int gy, int gz, PathOptions opts) {
        long startNanos = System.nanoTime();
        long deadline = startNanos + opts.timeoutMs * 1_000_000L;

        if (!world.isLoaded(sx, sz) || !world.isLoaded(gx, gz)) {
            return PathResult.failure("Start or goal chunk not loaded");
        }

        // Phase 1: chunk corridor
        List<long[]> corridorChunks = findChunkCorridor(world, sx, sz, gx, gz, opts, deadline);
        if (corridorChunks == null) return PathResult.failure("No chunk-level route");

        // Phase 2: block-level search constrained to the corridor
        Set<Long> allowed = new HashSet<>();
        for (long[] c : corridorChunks) allowed.add(c[0]);

        BlockAStar search = new BlockAStar(world, opts, deadline, allowed::contains);
        List<PathResult.PathPoint> path = search.search(sx, sy, sz, gx, gy, gz);
        int nodes = search.nodesExplored();

        if (path == null) {
            // Retry without corridor (goal chunk may sit behind a mispriced chunk link)
            BlockAStar retry = new BlockAStar(world, opts, deadline, null);
            path = retry.search(sx, sy, sz, gx, gy, gz);
            nodes += retry.nodesExplored();
        }
        if (path == null) return PathResult.failure("No block-level path (nodes: " + nodes + ")");

        PathResult.PathPoint last = path.get(path.size() - 1);
        if (Math.abs(last.x() - gx) > 1 || Math.abs(last.z() - gz) > 1) {
            return PathResult.failure("Corridor did not reach goal chunk");
        }

        long ms = (System.nanoTime() - startNanos) / 1_000_000L;
        double cost = estimateCost(path);
        return new PathResult(true, PathSmoother.smooth(world, path, opts), cost, nodes, ms, null);
    }

    /** BFS over chunks; a chunk links to a neighbor if any column pair is walkable-compatible. */
    private static List<long[]> findChunkCorridor(WorldView world, int sx, int sz, int gx, int gz,
                                                  PathOptions opts, long deadline) {
        int scx = Math.floorDiv(sx, 16), scz = Math.floorDiv(sz, 16);
        int gcx = Math.floorDiv(gx, 16), gcz = Math.floorDiv(gz, 16);

        record C(int x, int z, C parent) {
        }

        Map<Long, C> seen = new HashMap<>();
        ArrayDeque<C> queue = new ArrayDeque<>();
        C start = new C(scx, scz, null);
        seen.put(key(scx, scz), start);
        queue.add(start);

        while (!queue.isEmpty()) {
            if (System.nanoTime() > deadline) return null;
            C cur = queue.poll();
            if (cur.x == gcx && cur.z == gcz) {
                List<long[]> out = new ArrayList<>();
                for (C n = cur; n != null; n = n.parent) out.add(new long[]{key(n.x, n.z), n.x, n.z});
                java.util.Collections.reverse(out);
                return out;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = cur.x + dx, nz = cur.z + dz;
                    long k = key(nx, nz);
                    if (seen.containsKey(k)) continue;
                    if (!chunkLinkWalkable(world, cur.x, cur.z, nx, nz, opts)) continue;
                    seen.put(k, new C(nx, nz, cur));
                    queue.add(new C(nx, nz, cur));
                }
            }
        }
        return null;
    }

    /**
     * Conservative chunk-link test: exists a shared border column where both sides
     * are standable at a compatible height (equal, ±1 step, or a climb link).
     */
    static boolean chunkLinkWalkable(WorldView w, int ax, int az, int bx, int bz, PathOptions opts) {
        int minX = Math.min(ax, bx), maxX = Math.max(ax, bx);
        int minZ = Math.min(az, bz), maxZ = Math.max(az, bz);
        boolean xBorder = maxX - minX == 1 && ax != bx;
        boolean zBorder = maxZ - minZ == 1 && az != bz;
        if (!xBorder && !zBorder) return false; // diagonal chunk link requires both? keep conservative: reject pure diagonals
        if (xBorder && zBorder) {
            // Diagonal chunk: require a shared corner column pair to be linked
            int cx = ax < bx ? (ax + 1) * 16 - 1 : (bx + 1) * 16 - 1;
            int cz = az < bz ? (az + 1) * 16 - 1 : (bz + 1) * 16 - 1;
            return columnsCompatible(w, cx, cz, cx + 1, cz + 1, opts) || columnsCompatible(w, cx, cz + 1, cx + 1, cz, opts);
        }
        if (xBorder) {
            int borderX = ax < bx ? (Math.max(ax, bx)) * 16 - 1 : (Math.max(ax, bx)) * 16;
            for (int z = minZ * 16; z < (minZ + 1) * 16; z++) {
                if (columnsCompatible(w, borderX, z, borderX + 1, z, opts)) return true;
            }
        } else {
            int borderZ = az < bz ? (Math.max(az, bz)) * 16 - 1 : (Math.max(az, bz)) * 16;
            for (int x = minX * 16; x < (minX + 1) * 16; x++) {
                if (columnsCompatible(w, x, borderZ, x, borderZ + 1, opts)) return true;
            }
        }
        return false;
    }

    private static boolean columnsCompatible(WorldView w, int ax, int az, int bx, int bz, PathOptions opts) {
        if (!w.isLoaded(ax, az) || !w.isLoaded(bx, bz)) return false;
        int lo = Math.max(w.minY() + 1, w.maxY() - 384);
        int hi = w.maxY() - 1;
        for (int y = lo; y <= hi; y++) {
            if (standable(w, ax, y, az) && standable(w, bx, y, bz)) return true;
            if (standable(w, ax, y, az) && standable(w, bx, y + 1, bz)) return true;
            if (standable(w, ax, y + 1, az) && standable(w, bx, y, bz)) return true;
            BlockProps a = w.block(ax, y, az);
            if (a != null && a.climbable && standable(w, bx, y, bz)) return true;
            BlockProps b = w.block(bx, y, bz);
            if (b != null && b.climbable && standable(w, ax, y, az)) return true;
        }
        return false;
    }

    private static boolean standable(WorldView w, int x, int y, int z) {
        BlockProps support = w.block(x, y - 1, z);
        BlockProps feet = w.block(x, y, z);
        BlockProps head = w.block(x, y + 1, z);
        return support != null && feet != null && head != null
                && support.solid && feet.isPassable() && head.isPassable();
    }

    private static double estimateCost(List<PathResult.PathPoint> path) {
        double total = 0;
        PathResult.PathPoint prev = null;
        for (PathResult.PathPoint p : path) {
            if (prev != null) {
                int dx = Math.abs(p.x() - prev.x()), dy = Math.abs(p.y() - prev.y()), dz = Math.abs(p.z() - prev.z());
                total += Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
            }
            prev = p;
        }
        return total;
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | cz & 0xFFFFFFFFL;
    }
}
