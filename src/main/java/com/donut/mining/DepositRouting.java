package com.donut.mining;

/**
 * Pure decision for how the chest-deposit walk proceeds when a path search
 * comes back empty: follow the found path, fall back to bounded straight-line
 * steering when the chest is close enough that walls are unlikely to trap the
 * walk, or give up (cooldown) rather than grind. Free of Minecraft imports.
 */
public final class DepositRouting {
    public enum WalkDecision { FOLLOW_PATH, STEER_STRAIGHT, GIVE_UP }

    private DepositRouting() {
    }

    /**
     * @param pathFound        whether the path search produced a usable path
     * @param distanceToChest  straight-line distance to the chest in blocks
     * @param straightSteerMax maximum distance at which straight steering is attempted
     */
    public static WalkDecision decide(boolean pathFound, double distanceToChest, double straightSteerMax) {
        if (pathFound) return WalkDecision.FOLLOW_PATH;
        if (distanceToChest <= straightSteerMax) return WalkDecision.STEER_STRAIGHT;
        return WalkDecision.GIVE_UP;
    }
}
