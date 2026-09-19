package com.donut.pathfinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates candidate movements from a standable cell. Pure logic over
 * {@link WorldView}; each move carries a base cost plus a normalized danger
 * contribution in [0, 1] (1.0 = standing in lava).
 */
public final class MovementProcessor {
    /** Normalized danger of a move; used by A* to enforce maxDanger. */
    public record Move(int dx, int dy, int dz, double cost, float danger) {
    }

    private static final float WALK = 1.0f;
    private static final float DIAGONAL = 1.41421356f;
    private static final float STEP_UP = 1.15f;
    private static final float JUMP_GAP2 = 1.9f;
    private static final float JUMP_GAP3 = 2.7f;
    private static final float CLIMB = 2.0f;
    private static final float SWIM = 2.4f;

    private MovementProcessor() {
    }

    public static List<Move> moves(WorldView w, int x, int y, int z, PathOptions opts) {
        List<Move> out = new ArrayList<>(12);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                boolean diagonal = dx != 0 && dz != 0;
                addHorizontalMoves(w, x, y, z, dx, dz, diagonal, opts, out);
            }
        }
        addClimbMoves(w, x, y, z, out);
        return out;
    }

    private static void addHorizontalMoves(WorldView w, int x, int y, int z, int dx, int dz,
                                           boolean diagonal, PathOptions opts, List<Move> out) {
        int tx = x + dx, tz = z + dz;
        if (!w.isLoaded(tx, tz)) return;
        float base = diagonal ? DIAGONAL : WALK;

        BlockProps feet = w.block(tx, y, tz);
        BlockProps head = w.block(tx, y + 1, tz);
        if (feet == null || head == null) return;

        // Swim through water (never deliberately through lava)
        if (feet.liquid && !feet.dangerous && feet.isPassable()) {
            out.add(new Move(dx, 0, dz, base + SWIM, 0.3f));
            return;
        }

        // Straight walk: support below, feet+head clear
        BlockProps support = w.block(tx, y - 1, tz);
        if (support != null && support.solid && feet.isPassable() && head.isPassable()) {
            out.add(new Move(dx, 0, dz, base + coverPenalty(w, tx, y, tz, opts), danger(w, tx, y, tz)));
            return;
        }

        // Step up one block (auto-jump)
        if (feet.solid) {
            BlockProps upFeet = w.block(tx, y + 1, tz);
            BlockProps upHead = w.block(tx, y + 2, tz);
            BlockProps upSupport = w.block(tx, y, tz);
            if (upFeet != null && upHead != null && upFeet.isPassable() && upHead.isPassable()
                    && upSupport != null && upSupport.solid) {
                out.add(new Move(dx, 1, dz, base + STEP_UP + coverPenalty(w, tx, y + 1, tz, opts),
                        danger(w, tx, y + 1, tz)));
                return;
            }
        }

        if (feet.isPassable() && head.isPassable()) {
            // Fall: drop until support, bounded by maxFall
            int drop = 0;
            while (drop < opts.maxFall) {
                BlockProps below = w.block(tx, y - 1 - drop, tz);
                if (below == null) break;
                if (below.solid) {
                    float cost = base + 1.0f + 0.15f * drop;
                    // Floor sits at y-1-drop, so feet land at y-drop (one above it).
                    out.add(new Move(dx, -drop, dz, cost, danger(w, tx, y - drop, tz)));
                    return;
                }
                if (below.liquid && !below.dangerous) { // water landing
                    out.add(new Move(dx, -(drop + 1), dz, base + SWIM + 0.5f, 0.35f));
                    return;
                }
                drop++;
            }

            // Parkour: jump a 2- or 3-block gap to a supported landing
            if (opts.parkour) {
                for (int dist = 2; dist <= 3; dist++) {
                    int jx = x + dx * dist, jz = z + dz * dist;
                    if (!w.isLoaded(jx, jz)) continue;
                    BlockProps jFeet = w.block(jx, y, jz);
                    BlockProps jHead = w.block(jx, y + 1, jz);
                    BlockProps jSupport = w.block(jx, y - 1, jz);
                    if (jFeet != null && jHead != null && jSupport != null
                            && jFeet.isPassable() && jHead.isPassable() && jSupport.solid) {
                        float cost = dist == 2 ? JUMP_GAP2 : JUMP_GAP3;
                        out.add(new Move(dx * dist, 0, dz * dist, cost, danger(w, jx, y, jz)));
                    }
                }
            }
        }
    }

    private static void addClimbMoves(WorldView w, int x, int y, int z, List<Move> out) {
        BlockProps here = w.block(x, y, z);
        if (here == null) return;
        if (here.climbable) {
            BlockProps above = w.block(x, y + 1, z);
            if (above != null && above.climbable) out.add(new Move(0, 1, 0, CLIMB, 0f));
            BlockProps below = w.block(x, y - 1, z);
            if (below != null && (below.climbable || below.isPassable())) {
                out.add(new Move(0, -1, 0, CLIMB * 0.6f, 0f));
            }
        } else {
            // Grab a ladder/vine in an adjacent column
            BlockProps up = w.block(x, y + 1, z);
            if (up != null && up.climbable) out.add(new Move(0, 1, 0, CLIMB + 0.4f, 0f));
        }
    }

    /** Normalized danger of standing at a cell: self danger + adjacency. */
    private static float danger(WorldView w, int x, int y, int z) {
        float d = 0f;
        BlockProps self = w.block(x, y, z);
        if (self != null && self.dangerous) d += Math.min(1f, self.dangerWeight / 12f);
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0, 1}, {0, -1}};
        for (int i = 0; i < 4; i++) {
            BlockProps n = w.block(x + around[i][0], y, z + around[i][1]);
            if (n != null && n.dangerous) d += 0.25f * Math.min(1f, n.dangerWeight / 12f);
        }
        // Lava directly overhead is a falling hazard
        BlockProps up = w.block(x, y + 2, z);
        if (up != null && up.dangerous) d += 0.3f;
        return Math.min(1f, d);
    }

    /** Small penalty for exposed cells when preferCover is set. */
    private static float coverPenalty(WorldView w, int x, int y, int z, PathOptions opts) {
        if (!opts.preferCover) return 0f;
        boolean wall = false;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            BlockProps n = w.block(x + dir[0], y, z + dir[1]);
            if (n != null && n.solid) {
                wall = true;
                break;
            }
        }
        return wall ? 0f : 0.05f;
    }
}
