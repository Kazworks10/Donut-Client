package com.donut.pathfinding;

import java.util.List;

/**
 * Outcome of a path search: an ordered waypoint list (block coordinates of the
 * feet position), total cost and stats, or a failure reason.
 */
public record PathResult(boolean success, List<PathPoint> waypoints, double cost,
                         int nodesExplored, long timeMs, String failureReason) {

    public static PathResult failure(String reason) {
        return new PathResult(false, List.of(), 0, 0, 0, reason);
    }

    public boolean isEmpty() {
        return !success || waypoints.isEmpty();
    }

    /** A single waypoint in block space. */
    public record PathPoint(int x, int y, int z, float danger) {
        @Override
        public String toString() {
            return "(" + x + "," + y + "," + z + ")";
        }
    }
}
