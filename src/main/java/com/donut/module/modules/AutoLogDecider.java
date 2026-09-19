package com.donut.module.modules;

import java.util.Locale;

/**
 * Pure AutoLog trigger decision: given the current health sample and context
 * flags, decides whether to log out, after how many ticks, and why.
 * Minecraft-free; the combat-tag lookup is supplied lazily by the caller so it
 * is only evaluated when it can affect the decision.
 */
public final class AutoLogDecider {
    private AutoLogDecider() {
    }

    public record Decision(boolean trigger, int delayTicks, String reason) {
        public static final Decision NONE = new Decision(false, 0, null);
    }

    /**
     * @param health          current health + absorption
     * @param healthDropped   true when the health sample is lower than the previous tick's
     * @param threshold       log-out health threshold
     * @param respectCombatTag whether the combat-tag signal may veto the health trigger
     * @param combatTagged    lazy tag lookup; only called when it can change the outcome
     * @param logoutDelaySecs configured delay between trigger and disconnect
     */
    public static Decision decide(double health, boolean healthDropped, double threshold,
                                  boolean respectCombatTag, BooleanSupplier combatTagged,
                                  double logoutDelaySecs) {
        if (!healthDropped) return Decision.NONE;

        if (health < threshold) {
            if (respectCombatTag && combatTagged.getAsBoolean()) return Decision.NONE;
            return new Decision(true, delayTicks(logoutDelaySecs),
                    String.format(Locale.ROOT, "health %.1f < %.1f", health, threshold));
        }
        return Decision.NONE;
    }

    public static int delayTicks(double logoutDelaySecs) {
        return (int) Math.round(logoutDelaySecs * 20);
    }

    /** Case-insensitive "combat" keyword check used by the tag scanner. */
    public static boolean containsCombatKeyword(String s) {
        return s != null && s.toLowerCase(Locale.ROOT).contains("combat");
    }

    @FunctionalInterface
    public interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
