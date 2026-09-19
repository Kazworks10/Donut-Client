package com.donut.pathfinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Block-level A* over standable cells. Pure logic; allocated per search (the
 * per-tick hot-path constraint does not apply here — searches run off-thread).
 * Supports a "corridor" of allowed chunks produced by the hierarchical phase,
 * which prunes the search space dramatically on long routes.
 */
final class BlockAStar {
    static final class Node {
        final int x, y, z;
        final Node parent;
        final float g;
        float f;

        Node(int x, int y, int z, Node parent, float g, float f) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.parent = parent;
            this.g = g;
            this.f = f;
        }
    }

    /** Simple array-backed binary min-heap on f. */
    static final class Heap {
        private Node[] a = new Node[1024];
        private int size;

        void push(Node n) {
            if (size == a.length) {
                Node[] bigger = new Node[a.length * 2];
                System.arraycopy(a, 0, bigger, 0, size);
                a = bigger;
            }
            a[size] = n;
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >>> 1;
                if (a[p].f <= a[i].f) break;
                Node tmp = a[p];
                a[p] = a[i];
                a[i] = tmp;
                i = p;
            }
        }

        Node pop() {
            Node top = a[0];
            a[0] = a[--size];
            a[size] = null;
            int i = 0;
            while (true) {
                int l = 2 * i + 1, r = l + 1, m = i;
                if (l < size && a[l].f < a[m].f) m = l;
                if (r < size && a[r].f < a[m].f) m = r;
                if (m == i) break;
                Node tmp = a[m];
                a[m] = a[i];
                a[i] = tmp;
                i = m;
            }
            return top;
        }

        boolean isEmpty() {
            return size == 0;
        }
    }

    private final WorldView world;
    private final PathOptions opts;
    private final long deadlineNanos;
    private final java.util.function.LongPredicate corridor;
    private final Map<Long, Node> visited = new HashMap<>();
    private final Heap open = new Heap();
    private int explored;

    BlockAStar(WorldView world, PathOptions opts, long deadlineNanos, java.util.function.LongPredicate corridor) {
        this.world = world;
        this.opts = opts;
        this.deadlineNanos = deadlineNanos;
        this.corridor = corridor;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }

    /** Returns waypoints (excluding start, including goal area) or null on failure/timeout. */
    List<PathResult.PathPoint> search(int sx, int sy, int sz, int gx, int gy, int gz) {
        if (!world.isLoaded(sx, sz) || !world.isLoaded(gx, gz)) return null;
        Node start = new Node(sx, sy, sz, null, 0f, Heuristic.cost(sx, sy, sz, gx, gy, gz));
        visited.put(key(sx, sy, sz), start);
        open.push(start);

        while (!open.isEmpty()) {
            if (explored++ > opts.maxNodes) return null;
            if ((explored & 255) == 0 && System.nanoTime() > deadlineNanos) return null;

            Node cur = open.pop();
            if (isGoal(cur, gx, gy, gz)) return reconstruct(cur);

            for (MovementProcessor.Move mv : MovementProcessor.moves(world, cur.x, cur.y, cur.z, opts)) {
                int nx = cur.x + mv.dx(), ny = cur.y + mv.dy(), nz = cur.z + mv.dz();
                if (mv.danger() > opts.maxDanger) continue;
                long k = key(nx, ny, nz);
                if (visited.containsKey(k)) continue;
                if (corridor != null && !corridor.test(chunkKey(nx, nz))) continue;

                float ng = cur.g + (float) mv.cost();
                Node n = new Node(nx, ny, nz, cur, ng, ng + Heuristic.cost(nx, ny, nz, gx, gy, gz));
                visited.put(k, n);
                open.push(n);
            }
        }
        return null;
    }

    private boolean isGoal(Node n, int gx, int gy, int gz) {
        if (opts.goalRadius <= 0) {
            return n.x == gx && n.y == gy && n.z == gz;
        }
        double dx = n.x - gx, dz = n.z - gz;
        return dx * dx + dz * dz <= opts.goalRadius * opts.goalRadius && Math.abs(n.y - gy) <= 2;
    }

    private List<PathResult.PathPoint> reconstruct(Node goal) {
        List<PathResult.PathPoint> out = new ArrayList<>();
        for (Node n = goal; n != null; n = n.parent) {
            out.add(new PathResult.PathPoint(n.x, n.y, n.z, 0f));
        }
        java.util.Collections.reverse(out);
        out.remove(0); // drop the start cell; the executor begins where the player stands
        return out;
    }

    static long chunkKey(int x, int z) {
        return (long) (x >> 4) << 32 | (z >> 4) & 0xFFFFFFFFL;
    }

    /** Number of nodes expanded in the last search (for stats). */
    int nodesExplored() {
        return explored;
    }
}
