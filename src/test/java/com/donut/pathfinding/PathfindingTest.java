package com.donut.pathfinding;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests over synthetic in-memory worlds: flat plains, walls, gaps,
 * lava, stairs. Exercises the movement generator, A* core, hierarchical
 * search and the smoother end-to-end.
 */
class PathfindingTest {

    /** Dense map-backed world; default block everywhere is solid ground at y=64. */
    static final class GridWorld implements WorldView {
        final Map<Long, BlockProps> blocks = new HashMap<>();
        final int minX, maxX, minZ, maxZ;

        GridWorld(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    set(x, 63, z, BlockProps.SOLID); // ground
                    set(x, 64, z, BlockProps.AIR);   // feet
                    set(x, 65, z, BlockProps.AIR);   // head
                }
            }
        }

        void set(int x, int y, int z, BlockProps p) {
            blocks.put(key(x, y, z), p);
        }

        BlockProps get(int x, int y, int z) {
            return blocks.getOrDefault(key(x, y, z), BlockProps.SOLID);
        }

        static long key(int x, int y, int z) {
            return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (long) (y & 0xFFF);
        }

        @Override
        public BlockProps block(int x, int y, int z) {
            if (x < minX || x > maxX || z < minZ || z > maxZ || y < 0 || y > 127) return null;
            BlockProps p = blocks.get(key(x, y, z));
            if (p != null) return p;
            // Unset cells: solid at/below ground level, open air above.
            return y <= 63 ? BlockProps.SOLID : BlockProps.AIR;
        }

        @Override
        public boolean isLoaded(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        @Override
        public int minY() {
            return 0;
        }

        @Override
        public int maxY() {
            return 127;
        }
    }

    private static PathOptions opts() {
        return PathOptions.defaults().timeoutMs(5000).maxNodes(60_000).build();
    }

    private static List<PathResult.PathPoint> find(GridWorld w, int sx, int sz, int gx, int gz) {
        var result = HierarchicalAStar.compute(w, sx, 64, sz, gx, 64, gz, opts());
        assertTrue(result.success(), () -> "expected success, got: " + result.failureReason());
        return result.waypoints();
    }

    @Test
    void straightLineOnFlatGround() {
        GridWorld w = new GridWorld(-10, 10, -10, 10);
        var path = find(w, 0, 0, 5, 0);
        assertFalse(path.isEmpty());
        var last = path.get(path.size() - 1);
        assertEquals(5, last.x());
        assertEquals(0, last.z());
    }

    @Test
    void walksAroundWall() {
        GridWorld w = new GridWorld(-10, 10, -10, 10);
        // 1-block-high wall on x=3 from z=-5..5, with an opening at z=8 (outside wall range)
        for (int z = -5; z <= 5; z++) {
            w.set(3, 64, z, BlockProps.SOLID);
            w.set(3, 65, z, BlockProps.SOLID);
            w.set(3, 66, z, BlockProps.AIR);
        }
        var path = find(w, 0, 0, 6, 0);
        var last = path.get(path.size() - 1);
        assertEquals(6, last.x());
        // Every waypoint must be walkable: never inside the wall band
        for (var p : path) {
            assertFalse(p.x() == 3 && p.z() >= -5 && p.z() <= 5,
                    "path walks through the wall at " + p);
        }
    }

    @Test
    void climbsStairs() {
        GridWorld w = new GridWorld(-5, 15, -5, 5);
        // Staircase along x at z=0: feet 64 -> 65 -> 66 -> platform
        w.set(2, 64, 0, BlockProps.SOLID); // step A: feet 65
        w.set(3, 64, 0, BlockProps.SOLID); // landing col for step B
        w.set(4, 65, 0, BlockProps.SOLID); // step B: feet 66
        for (int x = 5; x <= 7; x++) w.set(x, 65, 0, BlockProps.SOLID); // platform: feet 66

        var result = HierarchicalAStar.compute(w, 0, 64, 0, 7, 66, 0, opts());
        assertTrue(result.success(), () -> "stair climb failed: " + result.failureReason());
        var last = result.waypoints().get(result.waypoints().size() - 1);
        assertEquals(7, last.x());
        assertEquals(66, last.y());
    }

    @Test
    void avoidsLava() {
        GridWorld w = new GridWorld(-10, 10, -10, 10);
        // Finite lava moat across z=0 (x -3..3); goal beyond it, so the only
        // maxDanger-respecting route goes around the ends.
        for (int x = -3; x <= 3; x++) {
            w.set(x, 63, 0, BlockProps.LAVA);
        }
        var result = HierarchicalAStar.compute(w, 0, 64, -2, 0, 64, 2,
                PathOptions.defaults().timeoutMs(5000).maxDanger(0.2f).build());
        assertTrue(result.success(), () -> "no route around moat: " + result.failureReason());
        for (var p : result.waypoints()) {
            assertFalse(w.get(p.x(), p.y(), p.z()).dangerous, "stepped in lava at " + p);
        }
    }

    @Test
    void parkourMovesOfferedWhenEnabled() {
        GridWorld w = new GridWorld(-6, 6, -6, 6);
        // 1-wide, 2-deep trench along x=1: the adjacent cell is a gap edge, so
        // the generator should offer a sprint-jump stride over it (dx=2).
        for (int z = -6; z <= 6; z++) {
            w.set(1, 63, z, BlockProps.AIR);
            w.set(1, 62, z, BlockProps.AIR);
            w.set(1, 61, z, BlockProps.SOLID);
        }
        // maxFall(1) stops the generator from offering a controlled drop into
        // the trench, so the jump stride is the only way across.
        var optsOn = PathOptions.defaults().parkour(true).maxFall(1).timeoutMs(5000).build();
        var moves = MovementProcessor.moves(w, 0, 64, 0, optsOn);
        assertTrue(moves.stream().anyMatch(m -> m.dx() == 2 && m.dz() == 0),
                "parkour stride move missing: " + moves);

        var optsOff = PathOptions.defaults().parkour(false).timeoutMs(5000).build();
        var noParkour = MovementProcessor.moves(w, 0, 64, 0, optsOff);
        assertTrue(noParkour.stream().noneMatch(m -> Math.abs(m.dx()) == 2),
                "parkour move offered while disabled");
    }

    @Test
    void refusesUnreachableGoal() {
        GridWorld w = new GridWorld(-6, 6, -6, 6);
        // Sealed box around the goal
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == 3;
                    if (shell) w.set(4 + dx, 64 + dy, 4 + dz, BlockProps.SOLID);
                }
            }
        }
        var result = HierarchicalAStar.compute(w, 0, 64, 0, 4, 64, 4,
                PathOptions.defaults().timeoutMs(3000).maxFall(1).build());
        assertFalse(result.success());
    }

    @Test
    void hierarchicalMatchesDirectSearch() {
        // Long flat run: hierarchical (chunk) phase must not lose the direct path
        GridWorld w = new GridWorld(-40, 40, -3, 3);
        var path = find(w, -40, 0, 40, 0);
        var last = path.get(path.size() - 1);
        assertEquals(40, last.x());
        assertTrue(path.size() < 90, "path should be near-straight, got " + path.size());
    }

    // ---- PathSmoother ---------------------------------------------------------

    @Test
    void smootherKeepsEndpointsAndCuts() {
        List<PathResult.PathPoint> zigzag = List.of(
                new PathResult.PathPoint(0, 64, 0, 0f),
                new PathResult.PathPoint(1, 64, 0, 0f),
                new PathResult.PathPoint(2, 64, 0, 0f),
                new PathResult.PathPoint(2, 64, 1, 0f),
                new PathResult.PathPoint(2, 64, 2, 0f),
                new PathResult.PathPoint(3, 64, 2, 0f));
        GridWorld w = new GridWorld(-10, 10, -10, 10);
        var out = PathSmoother.smooth(w, zigzag, opts());
        assertEquals(zigzag.get(0), out.get(0));
        assertEquals(zigzag.get(zigzag.size() - 1), out.get(out.size() - 1));
        assertTrue(out.size() <= zigzag.size(), "smoother should not add waypoints");
    }

    // ---- MovementProcessor units ------------------------------------------------

    @Test
    void movementOnFlatGroundIsEightDirectional() {
        GridWorld w = new GridWorld(-3, 3, -3, 3);
        var moves = MovementProcessor.moves(w, 0, 64, 0, opts());
        assertEquals(8, moves.size(), "flat ground should offer exactly 8 walk moves");
        for (var m : moves) {
            assertEquals(0, m.dy());
            assertTrue(m.danger() <= 0.5f);
        }
    }

    @Test
    void dangerPenalizesLavaAdjacency() {
        GridWorld w = new GridWorld(-3, 3, -3, 3);
        w.set(1, 63, 0, BlockProps.LAVA);
        w.set(1, 64, 0, BlockProps.LAVA);
        w.set(1, 65, 0, BlockProps.AIR);
        var moves = MovementProcessor.moves(w, 0, 64, 0, opts());
        double straight = Double.MAX_VALUE;
        for (var m : moves) {
            if (m.dx() == 1 && m.dz() == 0) straight = m.cost();
        }
        double away = Double.MAX_VALUE;
        for (var m : moves) {
            if (m.dx() == -1 && m.dz() == 0) away = m.cost();
        }
        assertTrue(straight > away, "walking toward lava must cost more than walking away");
    }
}
