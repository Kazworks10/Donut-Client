package com.donut.mining;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Headless tests for deposit-item selection: filtering by item id, stack size
 * ordering and the stable slot-index tie-break.
 */
class DepositPlannerTest {
    record V(String id, int count) implements DepositPlanner.StackView {
        @Override public String itemId() { return id; }
        @Override public int count() { return count; }
    }

    private static final Set<String> TRASH = Set.of(
            "minecraft:cobblestone", "minecraft:dirt", "minecraft:gravel");

    @Test
    void selectsOnlyConfiguredItems() {
        List<DepositPlanner.StackView> stacks = List.of(
                new V("minecraft:cobblestone", 64),
                new V("minecraft:diamond", 5),
                new V("minecraft:dirt", 30),
                new V("minecraft:stone", 64));
        assertArrayEquals(new int[]{0, 2}, DepositPlanner.selectForDeposit(stacks, TRASH));
    }

    @Test
    void largestStacksComeFirst() {
        List<DepositPlanner.StackView> stacks = List.of(
                new V("minecraft:dirt", 3),
                new V("minecraft:dirt", 64),
                new V("minecraft:gravel", 10));
        assertArrayEquals(new int[]{1, 2, 0}, DepositPlanner.selectForDeposit(stacks, TRASH));
    }

    @Test
    void tiesBreakTowardLowerSlotIndex() {
        List<DepositPlanner.StackView> stacks = List.of(
                new V("minecraft:air", 0),
                new V("minecraft:dirt", 32),
                new V("minecraft:gravel", 32),
                new V("minecraft:dirt", 32));
        assertArrayEquals(new int[]{1, 2, 3}, DepositPlanner.selectForDeposit(stacks, TRASH));
    }

    @Test
    void emptyAndZeroCountStacksAreSkipped() {
        List<DepositPlanner.StackView> stacks = Arrays.asList(
                new V("minecraft:cobblestone", 0),
                new V("", 64),
                (DepositPlanner.StackView) null,
                new V("minecraft:dirt", 1));
        assertArrayEquals(new int[]{3}, DepositPlanner.selectForDeposit(stacks, TRASH));
    }

    @Test
    void emptyConfigDepositsNothing() {
        List<DepositPlanner.StackView> stacks = List.of(new V("minecraft:dirt", 64));
        assertArrayEquals(new int[0], DepositPlanner.selectForDeposit(stacks, Set.of()));
    }

    @Test
    void allNonTrashDepositsNothing() {
        List<DepositPlanner.StackView> stacks = List.of(
                new V("minecraft:diamond_sword", 1),
                new V("minecraft:cooked_beef", 64));
        assertArrayEquals(new int[0], DepositPlanner.selectForDeposit(stacks, TRASH));
    }
}
