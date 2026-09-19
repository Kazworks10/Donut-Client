package com.donut.schematic;

import com.donut.util.Pos3Key;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure placement ordering over schematic cells. Produces a list of [x, y, z]
 * local coordinates in build order:
 * <ul>
 *   <li><b>layer</b> — bottom-up layers (default, stable);</li>
 *   <li><b>spiral</b> — bottom-up, spiral within each layer;</li>
 *   <li><b>nearest</b> — always nearest reachable block from origin (greedy).</li>
 * </ul>
 * A support pass then reorders attachable blocks after their host block so
 * torches/doors never pop off.
 */
public final class PlacementPlanner {
    private PlacementPlanner() {
    }

    public static List<int[]> plan(SchematicData s, String mode, int originX, int originZ) {
        List<int[]> cells = new ArrayList<>();
        s.forEachNonAir((x, y, z, id) -> cells.add(new int[]{x, y, z, id}));

        switch (mode == null ? "layer" : mode.toLowerCase()) {
            case "spiral" -> cells.sort(Comparator
                    .comparingInt((int[] c) -> c[1])
                    .thenComparingInt(c -> ringOrder(c[0], c[2])));
            case "nearest" -> cells.sort(Comparator
                    .comparingInt((int[] c) -> c[1])
                    .thenComparingInt(c -> dist2(c[0], c[2], originX, originZ)));
            default -> cells.sort(Comparator
                    .comparingInt((int[] c) -> c[1])
                    .thenComparingInt(c -> c[2])
                    .thenComparingInt(c -> c[0]));
        }

        applySupportOrder(s, cells);
        return cells;
    }

    private static int ringOrder(int x, int z) {
        int ring = Math.max(Math.abs(x), Math.abs(z));
        return ring * 1000 + (Math.abs(x) + Math.abs(z));
    }

    private static int dist2(int x, int z, int ox, int oz) {
        int dx = x - ox, dz = z - oz;
        return dx * dx + dz * dz;
    }

    /**
     * Moves attachable blocks (need a supporting neighbor below/side) to directly
     * after their support cell when that support is part of the schematic and
     * appears later. Simple O(n) index-map pass.
     */
    private static void applySupportOrder(SchematicData s, List<int[]> cells) {
        int n = cells.size();
        long[] keys = new long[n];
        for (int i = 0; i < n; i++) {
            int[] c = cells.get(i);
            keys[i] = key(c[0], c[1], c[2]);
        }
        // Index of each cell in the current order
        java.util.HashMap<Long, Integer> order = new java.util.HashMap<>(n * 2);
        for (int i = 0; i < n; i++) order.put(keys[i], i);

        boolean[] moved = new boolean[n];
        List<int[]> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int[] c = cells.get(i);
            if (moved[i]) continue;
            result.add(c);
            moved[i] = true;
            // If the cell directly above is attachable, place it right after its
            // support block so it never pops off.
            int above = order.getOrDefault(key(c[0], c[1] + 1, c[2]), -1);
            if (above > i && !moved[above]) {
                String upName = s.palette().name(cells.get(above)[3]);
                if (isAttachable(upName)) {
                    result.add(cells.get(above));
                    moved[above] = true;
                }
            }
        }
        // Any cells skipped by the pass (shouldn't happen) are appended
        for (int i = 0; i < n; i++) if (!moved[i]) result.add(cells.get(i));
        cells.clear();
        cells.addAll(result);
    }

    private static boolean isAttachable(String name) {
        return name.endsWith("torch") || name.endsWith("rail") || name.endsWith("carpet")
                || name.contains("door") || name.contains("pressure_plate")
                || name.contains("button") || name.contains("sign") || name.contains("banner")
                || name.contains("ladder") || name.contains("lever") || name.contains("snow");
    }

    private static long key(int x, int y, int z) {
        return Pos3Key.pack(x, y, z);
    }
}
