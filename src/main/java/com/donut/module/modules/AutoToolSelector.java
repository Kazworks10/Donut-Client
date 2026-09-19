package com.donut.module.modules;

import java.util.List;

/**
 * Pure AutoTool decision core: pick the hotbar slot with the best mining speed
 * for the block being attacked, and only leave the current slot when the best
 * is strictly better. Ties stay on the lower slot. Minecraft-free.
 */
public final class AutoToolSelector {
    private AutoToolSelector() {
    }

    /** Scores for hotbar slots 0-8; negative = not a usable tool for the block. */
    public record Scores(List<Double> bySlot) {
        public Scores {
            if (bySlot.size() != 9) throw new IllegalArgumentException("need 9 hotbar scores");
        }

        public double score(int slot) {
            return bySlot.get(slot);
        }
    }

    /**
     * The slot to swap to, or -1 to keep the current one. Strict improvement is
     * required (a tie never switches); ineligible stacks (score &lt; 0) never win.
     */
    public static int select(Scores scores, int currentSlot) {
        int best = -1;
        double bestSpeed = 0;
        for (int slot = 0; slot < 9; slot++) {
            double speed = scores.score(slot);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = slot;
            }
        }
        if (best < 0 || best == currentSlot) return -1;
        return scores.score(currentSlot) < bestSpeed ? best : -1;
    }
}
