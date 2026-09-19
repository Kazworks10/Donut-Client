package com.donut.pathfinding;

import com.donut.mining.MovementSteering;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Follows a {@link PathResult} by driving {@link MovementInputOverride} each
 * tick: player-local steering toward the current waypoint, auto-jump for steps
 * and gaps, sprint when safely fed, and stuck detection that triggers a replan.
 * Runs on the client thread only.
 */
public final class PathExecutor {
    public enum State { IDLE, FOLLOWING, ARRIVED, STUCK, FAILED }

    private List<PathResult.PathPoint> path;
    private int index;
    private Vec3d lastPos;
    private long slowSinceMs = -1;
    private final long stuckTimeoutMs;
    private State state = State.IDLE;
    private Vec3d target;
    private float sprintFoodThreshold = 0.8f;

    /** Set by the owning module to recompute a path from the stuck position. */
    public ReplanHook replanHook;

    public interface ReplanHook {
        void replan(Vec3d from);
    }

    public PathExecutor(long stuckTimeoutMs) {
        this.stuckTimeoutMs = stuckTimeoutMs;
    }

    public void follow(List<PathResult.PathPoint> path, Vec3d target) {
        this.path = path;
        this.target = target;
        this.index = 0;
        this.state = path == null || path.isEmpty() ? State.FAILED : State.FOLLOWING;
        this.lastPos = null;
        this.slowSinceMs = -1;
        if (state == State.FOLLOWING) MovementInputOverride.begin();
    }

    public void stop() {
        state = State.IDLE;
        MovementInputOverride.end();
    }

    public State state() {
        return state;
    }

    public int waypointIndex() {
        return index;
    }

    public int pathLength() {
        return path == null ? 0 : path.size();
    }

    public void tick(MinecraftClient client) {
        if (state != State.FOLLOWING) return;
        ClientPlayerEntity player = client.player;
        if (player == null || path == null || index >= path.size()) {
            arrive();
            return;
        }

        PathResult.PathPoint wp = path.get(index);
        Vec3d targetCenter = new Vec3d(wp.x() + 0.5, wp.y(), wp.z() + 0.5);
        Vec3d pos = player.getPos();
        double dx = targetCenter.x - pos.x;
        double dz = targetCenter.z - pos.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        int dy = wp.y() - player.getBlockY();

        // Waypoint reached?
        if (horiz < 0.55 && Math.abs(dy) <= 1.2) {
            index++;
            if (index >= path.size()) {
                arrive();
            }
            return;
        }

        // Steering: world-space direction into player-local forward/strafe
        double len = Math.max(1e-6, horiz);
        double dirX = dx / len, dirZ = dz / len;
        double yawRad = Math.toRadians(player.getYaw());
        double fwdX = -Math.sin(yawRad), fwdZ = Math.cos(yawRad);
        double leftX = Math.cos(yawRad), leftZ = Math.sin(yawRad);
        float f = (float) (dirX * fwdX + dirZ * fwdZ);
        float s = (float) (dirX * leftX + dirZ * leftZ);

        // Jump for one-block steps and nearby climb waypoints
        boolean jump = false;
        if (dy == 1 && horiz < 1.4) jump = true;
        if (dy == 0 && horiz > 1.1 && horiz < 3.3) jump = true; // gap crossing

        // Sprint when the path ahead is long and food is healthy
        boolean sprint = player.getHungerManager().getFoodLevel() >= (int) (20 * sprintFoodThreshold)
                && f > 0.85f && (path.size() - index) > 4;
        player.setSprinting(sprint);

        MovementSteering.applyLocal(f, s, jump, false, sprint);

        // Look toward the waypoint (real camera rotation; modules may queue fancier turns)
        RotationManagerCompat.look(client, targetCenter);

        // Stuck detection
        if (lastPos != null) {
            double moved = pos.squaredDistanceTo(lastPos.x, lastPos.y, lastPos.z);
            if (moved < 0.0025) {
                if (slowSinceMs < 0) slowSinceMs = System.currentTimeMillis();
                else if (System.currentTimeMillis() - slowSinceMs > stuckTimeoutMs) {
                    state = State.STUCK;
                    MovementInputOverride.stop();
                    if (replanHook != null) replanHook.replan(pos);
                    return;
                }
            } else {
                slowSinceMs = -1;
            }
        }
        lastPos = pos;
    }

    private void arrive() {
        state = State.ARRIVED;
        MovementInputOverride.stop();
    }

    /**
     * Small bridge so the executor can aim the player without dragging in the
     * rotation queue here (kept as a static shim for clarity).
     */
    private static final class RotationManagerCompat {
        static void look(MinecraftClient client, Vec3d target) {
            com.donut.rotation.RotationManager.lookAt(target,
                    com.donut.rotation.RotationManager.RotationOptions.quick().duration(0.12f));
        }
    }
}
