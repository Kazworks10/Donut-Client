package com.donut.mining;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the vein flood-fill: connectivity, BFS visit order,
 * max-block cap and id filtering.
 */
class VeinMinerTest {
    private static VeinMiner.BlockLookup world(Map<Long, String> blocks) {
        return (x, y, z) -> blocks.get(VeinMiner.key(x, y, z));
    }

    @Test
    void singleBlockYieldsJustTheSeed() {
        Map<Long, String> w = new HashMap<>();
        w.put(VeinMiner.key(0, 0, 0), "diamond_ore");
        List<int[]> out = VeinMiner.collect(world(w), 0, 0, 0, 64);
        assertEquals(1, out.size());
        assertEquals(0, out.get(0)[0]);
        assertEquals(0, out.get(0)[1]);
        assertEquals(0, out.get(0)[2]);
    }

    @Test
    void unknownSeedYieldsNothing() {
        List<int[]> out = VeinMiner.collect(world(Map.of()), 5, 5, 5, 64);
        assertTrue(out.isEmpty());
    }

    @Test
    void zeroCapYieldsNothing() {
        Map<Long, String> w = new HashMap<>();
        w.put(VeinMiner.key(0, 0, 0), "diamond_ore");
        assertTrue(VeinMiner.collect(world(w), 0, 0, 0, 0).isEmpty());
    }

    @Test
    void diagonalNeighborsAreConnected() {
        Map<Long, String> w = new HashMap<>();
        w.put(VeinMiner.key(0, 0, 0), "diamond_ore");
        w.put(VeinMiner.key(1, 0, 1), "diamond_ore"); // diagonal touch
        List<int[]> out = VeinMiner.collect(world(w), 0, 0, 0, 64);
        assertEquals(2, out.size());
        assertEquals(1, out.get(1)[0]);
        assertEquals(1, out.get(1)[2]);
    }

    @Test
    void differentIdsAreIgnored() {
        Map<Long, String> w = new HashMap<>();
        w.put(VeinMiner.key(0, 0, 0), "diamond_ore");
        w.put(VeinMiner.key(1, 0, 0), "stone");
        w.put(VeinMiner.key(0, 0, 1), "diamond_ore");
        List<int[]> out = VeinMiner.collect(world(w), 0, 0, 0, 64);
        assertEquals(2, out.size());
        assertTrue(out.stream().noneMatch(c -> c[0] == 1 && c[2] == 0));
    }

    @Test
    void visitOrderIsNearestFirst() {
        Map<Long, String> w = new HashMap<>();
        w.put(VeinMiner.key(0, 0, 0), "iron_ore");
        w.put(VeinMiner.key(1, 0, 0), "iron_ore"); // distance 1
        w.put(VeinMiner.key(0, 1, 0), "iron_ore"); // distance 1
        w.put(VeinMiner.key(2, 0, 0), "iron_ore"); // distance 2, reached via (1,0,0)
        List<int[]> out = VeinMiner.collect(world(w), 0, 0, 0, 64);
        assertEquals(4, out.size());
        assertEquals(0, out.get(0)[0]); // seed first
        int idxA = indexOf(out, 1, 0, 0);
        int idxB = indexOf(out, 0, 1, 0);
        int idxC = indexOf(out, 2, 0, 0);
        assertTrue(idxC > idxA, "distance-2 cell must come after its distance-1 neighbor");
        assertTrue(idxC > idxB, "distance-2 cell must come after both distance-1 cells");
    }

    @Test
    void capLimitsVeinSize() {
        Map<Long, String> w = new HashMap<>();
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    w.put(VeinMiner.key(x, y, z), "iron_ore"); // 27-cell cube
        List<int[]> out = VeinMiner.collect(world(w), 0, 0, 0, 10);
        assertEquals(10, out.size());
        assertEquals(0, out.get(0)[0]); // seed always first
        assertTrue(out.stream().allMatch(c -> Math.abs(c[0]) <= 1 && Math.abs(c[1]) <= 1 && Math.abs(c[2]) <= 1));
    }

    @Test
    void disconnectedSameIdIsNotIncluded() {
        Map<Long, String> w = new HashMap<>();
        w.put(VeinMiner.key(0, 0, 0), "iron_ore");
        w.put(VeinMiner.key(3, 0, 0), "iron_ore"); // separate vein
        List<int[]> out = VeinMiner.collect(world(w), 0, 0, 0, 64);
        assertEquals(1, out.size());
    }

    private static int indexOf(List<int[]> cells, int x, int y, int z) {
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            if (c[0] == x && c[1] == y && c[2] == z) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------ RetryBudget

    @Test
    void retryBudgetSkipsOnlyAfterMaxAttempts() {
        VeinMiner.RetryBudget budget = new VeinMiner.RetryBudget(VeinMiner.DEFAULT_MAX_ATTEMPTS);
        long k = VeinMiner.key(1, 2, 3);
        for (int i = 0; i < VeinMiner.DEFAULT_MAX_ATTEMPTS - 1; i++) {
            assertTrue(!budget.fail(k), "must not skip before the attempt limit");
        }
        assertTrue(budget.fail(k), "exactly the max attempt must skip");
    }

    @Test
    void retryBudgetAccountsPositionsIndependently() {
        VeinMiner.RetryBudget budget = new VeinMiner.RetryBudget(2);
        long a = VeinMiner.key(0, 0, 0);
        long b = VeinMiner.key(1, 0, 0);
        assertTrue(!budget.fail(a), "first of two attempts must not skip");
        assertEquals(1, budget.failuresOf(a));
        assertEquals(0, budget.failuresOf(b), "unrelated position must be unaffected");
        assertTrue(!budget.fail(b), "b's first attempt must not skip either");
    }

    @Test
    void retryBudgetClearForgetsOnePosition() {
        VeinMiner.RetryBudget budget = new VeinMiner.RetryBudget(2);
        long k = VeinMiner.key(4, 5, 6);
        budget.fail(k);
        budget.clear(k);
        assertEquals(0, budget.failuresOf(k));
        assertTrue(!budget.fail(k), "after clear the position starts fresh");
    }

    @Test
    void retryBudgetResetForgetsEverything() {
        VeinMiner.RetryBudget budget = new VeinMiner.RetryBudget(1);
        budget.fail(VeinMiner.key(0, 0, 0));
        budget.fail(VeinMiner.key(9, 9, 9));
        budget.reset();
        assertEquals(0, budget.failuresOf(VeinMiner.key(0, 0, 0)));
        assertEquals(0, budget.failuresOf(VeinMiner.key(9, 9, 9)));
    }

    @Test
    void retryBudgetClampsNonPositiveMax() {
        VeinMiner.RetryBudget budget = new VeinMiner.RetryBudget(0);
        assertTrue(budget.fail(VeinMiner.key(1, 1, 1)), "a budget of 0 must still require one attempt");
    }
}
