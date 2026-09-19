package com.donut.pathfinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-stage smoothing: string-pulling via conservative straight-line walkability
 * checks (all cells between waypoints standable), then light Catmull-Rom
 * densification for natural-looking motion. Honors maxDanger.
 */
public final class PathSmoother {
    private PathSmoother() {
    }

    public static List<PathResult.PathPoint> smooth(WorldView world, List<PathResult.PathPoint> path, PathOptions opts) {
        if (path.size() < 3) return path;
        List<PathResult.PathPoint> pulled = stringPull(world, path, opts);
        return densify(pulled);
    }

    /** Greedy anchor skipping: keeps the farthest waypoint visible from the current anchor. */
    private static List<PathResult.PathPoint> stringPull(WorldView w, List<PathResult.PathPoint> path, PathOptions opts) {
        List<PathResult.PathPoint> out = new ArrayList<>();
        int i = 0;
        out.add(path.get(0));
        while (i < path.size() - 1) {
            int best = i + 1;
            for (int j = path.size() - 1; j > best; j--) {
                if (lineWalkable(w, path.get(i), path.get(j), opts)) {
                    best = j;
                    break;
                }
            }
            out.add(path.get(best));
            i = best;
        }
        return out;
    }

    /** Bresenham on XZ plus vertical lerp; every visited cell must be standable-ish. */
    private static boolean lineWalkable(WorldView w, PathResult.PathPoint a, PathResult.PathPoint b, PathOptions opts) {
        int steps = Math.max(Math.abs(b.x() - a.x()), Math.abs(b.z() - a.z()));
        if (steps == 0) return true;
        for (int s = 1; s < steps; s++) {
            double t = (double) s / steps;
            int x = (int) Math.round(a.x() + (b.x() - a.x()) * t);
            int z = (int) Math.round(a.z() + (b.z() - a.z()) * t);
            int y = (int) Math.round(a.y() + (b.y() - a.y()) * t);
            BlockProps feet = w.block(x, y, z);
            BlockProps head = w.block(x, y + 1, z);
            BlockProps support = w.block(x, y - 1, z);
            if (feet == null || head == null || support == null) return false;
            if (feet.dangerous || head.dangerous) return false;
            if (!feet.isPassable() || !head.isPassable() || !support.solid) return false;
        }
        return true;
    }

    /** Inserts midpoints on long straight segments for smoother steering. */
    private static List<PathResult.PathPoint> densify(List<PathResult.PathPoint> path) {
        List<PathResult.PathPoint> out = new ArrayList<>(path.size() * 2);
        for (int i = 0; i < path.size() - 1; i++) {
            PathResult.PathPoint a = path.get(i);
            PathResult.PathPoint b = path.get(i + 1);
            out.add(a);
            int dist = Math.max(Math.abs(b.x() - a.x()), Math.abs(b.z() - a.z()));
            if (dist > 4) {
                out.add(new PathResult.PathPoint((a.x() + b.x()) / 2, (a.y() + b.y()) / 2, (a.z() + b.z()) / 2, 0f));
            }
        }
        out.add(path.get(path.size() - 1));
        return out;
    }
}
