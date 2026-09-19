package com.donut.mining;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure vein flood-fill for AutoMine. Given a block-id lookup, collects the
 * connected same-id cells around a seed in deterministic nearest-first (BFS)
 * order. Deliberately free of Minecraft imports so it is unit-testable
 * headless; {@code AutoMine} supplies the {@link BlockLookup} adapter.
 */
public final class VeinMiner {
    private VeinMiner() {
    }

    /** Minimal world abstraction: block registry id (no namespace) by position. */
    public interface BlockLookup {
        /** Id like {@code diamond_ore}, or null when the cell is air/unknown. */
        String idAt(int x, int y, int z);
    }

    private static final int[][] NEIGHBORS = neighbors();

    /**
     * Collects up to {@code maxBlocks} cells connected to the seed (26-way
     * connectivity) whose id equals the seed's id. The seed is always first;
     * the rest follow BFS ring order (nearest cells first), which paces
     * mining from the closest block outward.
     */
    public static List<int[]> collect(BlockLookup world, int sx, int sy, int sz, int maxBlocks) {
        List<int[]> out = new ArrayList<>();
        if (maxBlocks <= 0) return out;
        String seedId = world.idAt(sx, sy, sz);
        if (seedId == null) return out;

        Set<Long> visited = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        visited.add(key(sx, sy, sz));
        queue.add(new int[]{sx, sy, sz});
        out.add(new int[]{sx, sy, sz});

        while (!queue.isEmpty() && out.size() < maxBlocks) {
            int[] c = queue.poll();
            for (int[] n : NEIGHBORS) {
                if (out.size() >= maxBlocks) break;
                int x = c[0] + n[0], y = c[1] + n[1], z = c[2] + n[2];
                long k = key(x, y, z);
                if (!visited.add(k)) continue;
                if (!seedId.equals(world.idAt(x, y, z))) continue;
                queue.add(new int[]{x, y, z});
                out.add(new int[]{x, y, z});
            }
        }
        return out;
    }

    private static int[][] neighbors() {
        List<int[]> list = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    list.add(new int[]{dx, dy, dz});
                }
        return list.toArray(new int[0][]);
    }

    /** Stable position packing shared with tests. */
    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }
}
