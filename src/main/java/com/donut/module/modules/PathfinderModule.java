package com.donut.module.modules;

import com.donut.module.Category;
import com.donut.module.Module;
import com.donut.module.settings.BooleanSetting;
import com.donut.module.settings.EnumSetting;
import com.donut.module.settings.NumberSetting;
import com.donut.pathfinding.MinecraftWorldView;
import com.donut.pathfinding.PathOptions;
import com.donut.pathfinding.PathResult;
import com.donut.pathfinding.PathExecutor;
import com.donut.pathfinding.Pathfinder;
import com.donut.rotation.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.CompletableFuture;

/**
 * Walks the player to a point (the looked-at block, or explicit coordinates via
 * chat command later). Search runs off-thread; following is pure input
 * injection with stuck detection and automatic replanning.
 */
public final class PathfinderModule extends Module {
    public enum GoalMode { LOOKED_AT, COORDINATES }

    private final EnumSetting<GoalMode> goalMode = new EnumSetting<>("Goal", GoalMode.LOOKED_AT,
            "Target selection for the next run");
    private final NumberSetting coordX = new NumberSetting("X", 0, -30_000_000, 30_000_000, 1, 0, "Goal X");
    private final NumberSetting coordZ = new NumberSetting("Z", 0, -30_000_000, 30_000_000, 1, 0, "Goal Z");
    private final NumberSetting stuckTimeout = new NumberSetting("Stuck Timeout (s)", 3.0, 1.0, 15.0, 0.5, 1,
            "Seconds without movement before replanning");
    private final BooleanSetting parkour = new BooleanSetting("Parkour", true, "Allow jumps and gaps");
    private final BooleanSetting avoidDanger = new BooleanSetting("Avoid Danger", true,
            "Heavily weight lava/cactus/fire");

    private PathExecutor executor;
    private CompletableFuture<PathResult> pending;
    private String lastStatus = "idle";

    public PathfinderModule() {
        super("Pathfinder", "Walks you to the block you look at", Category.MOVEMENT);
        settings.add(goalMode);
        settings.add(coordX);
        settings.add(coordZ);
        settings.add(stuckTimeout);
        settings.add(parkour);
        settings.add(avoidDanger);
    }

    @Override
    protected void init() {
        listenTick();
    }

    @Override
    protected void onEnable() {
        lastStatus = "idle";
    }

    @Override
    protected void onDisable() {
        if (executor != null) executor.stop();
        pending = null;
    }

    public String status() {
        return lastStatus;
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        if (executor == null) {
            executor = new PathExecutor((long) (stuckTimeout.get() * 1000));
            executor.replanHook = this::replan;
        }
        executor.tick(client);
        lastStatus = switch (executor.state()) {
            case IDLE -> goalMode.get() == GoalMode.LOOKED_AT ? "idle (look at a block)" : "idle (set X/Z)";
            case FOLLOWING -> "following (" + executor.waypointIndex() + "/" + executor.pathLength() + ")";
            case ARRIVED -> "arrived";
            case STUCK -> "stuck - replanning";
            case FAILED -> "no path found";
        };

        // Start a new run when idle: looked-at block within reach of crosshair.
        if (executor.state() == PathExecutor.State.IDLE && !RotationManager.isBusy()) {
            if (goalMode.get() == GoalMode.LOOKED_AT && client.crosshairTarget != null
                    && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockPos looked = BlockPos.ofFloored(client.crosshairTarget.getPos());
                // Ignore targets under your own feet (prevents accidental self-targeting)
                if (!looked.equals(client.player.getBlockPos())) {
                    goTo(client, Vec3d.ofCenter(looked));
                }
            }
        }
    }

    private void goTo(MinecraftClient client, Vec3d target) {
        PathOptions.Builder b = PathOptions.defaults()
                .parkour(parkour.get())
                .preferCover(avoidDanger.get())
                .timeoutMs(10_000)
                .maxNodes(120_000);
        if (!avoidDanger.get()) b.maxDanger(10f);
        lastGoalTarget = target;
        pending = Pathfinder.goTo(client.world, client.player.getPos(), target, b.build());
        lastStatus = "searching...";
        pending.thenAccept(result -> client.execute(() -> {
            if (result.success()) {
                executor.follow(result.waypoints(), target);
            } else {
                lastStatus = "no path: " + result.failureReason();
            }
        }));
    }

    private void replan(Vec3d from) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        Vec3d target = lastGoalTarget;
        if (target == null) return;
        pending = Pathfinder.goTo(client.world, from, target,
                PathOptions.defaults().parkour(parkour.get()).timeoutMs(10_000).build());
        pending.thenAccept(result -> client.execute(() -> {
            if (result.success()) executor.follow(result.waypoints(), target);
            else executor.stop();
        }));
    }

    private Vec3d lastGoalTarget;
}
