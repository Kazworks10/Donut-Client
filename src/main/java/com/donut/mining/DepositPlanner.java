package com.donut.mining;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure deposit-item selection for AutoMine's chest deposit. Decides which
 * inventory stacks qualify for deposit and in which order (largest stack
 * first; ties broken by lower slot index so behavior is stable). Free of
 * Minecraft imports; {@code AutoMine} supplies the {@link StackView} adapter
 * over the player inventory.
 */
public final class DepositPlanner {
    private DepositPlanner() {
    }

    /** Minimal view of one inventory stack. */
    public interface StackView {
        /** Item registry id as given to {@code depositIds} (same namespace form). */
        String itemId();

        int count();
    }

    /**
     * Returns the slot indices of stacks eligible for deposit, largest stack
     * first (ties broken by lower slot index). Empty stacks, zero counts and
     * items outside {@code depositIds} are skipped.
     */
    public static int[] selectForDeposit(List<StackView> stacks, Set<String> depositIds) {
        if (depositIds == null || depositIds.isEmpty()) return new int[0];
        List<int[]> candidates = new ArrayList<>(); // {slot, count}
        for (int i = 0; i < stacks.size(); i++) {
            StackView s = stacks.get(i);
            if (s == null) continue;
            String id = s.itemId();
            if (id == null || id.isEmpty()) continue;
            if (!depositIds.contains(id)) continue;
            if (s.count() <= 0) continue;
            candidates.add(new int[]{i, s.count()});
        }
        candidates.sort((a, b) -> b[1] != a[1]
                ? Integer.compare(b[1], a[1])
                : Integer.compare(a[0], b[0]));
        int[] out = new int[candidates.size()];
        for (int i = 0; i < out.length; i++) out[i] = candidates.get(i)[0];
        return out;
    }
}
