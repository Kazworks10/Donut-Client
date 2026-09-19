package com.donut.pathfinding;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.CompletableFuture;

/**
 * Public pathfinding facade: {@code Pathfinder.goTo(world, start, target, options)}.
 * Searches run asynchronously (virtual threads by default); results are pure
 * waypoints consumed by {@link PathExecutor} on the client thread.
 */
public final class Pathfinder {
    private Pathfinder() {
    }

    public static CompletableFuture<PathResult> goTo(net.minecraft.world.World world, Vec3d start, Vec3d target, PathOptions opts) {
        var view = new MinecraftWorldView(world);
        return HierarchicalAStar.findPath(view,
                (int) Math.floor(start.x), (int) Math.floor(start.y), (int) Math.floor(start.z),
                (int) Math.floor(target.x), (int) Math.floor(target.y), (int) Math.floor(target.z),
                opts);
    }

    public static CompletableFuture<PathResult> goTo(net.minecraft.world.World world, BlockPos start, BlockPos goal, PathOptions opts) {
        var view = new MinecraftWorldView(world);
        return HierarchicalAStar.findPath(view,
                start.getX(), start.getY(), start.getZ(),
                goal.getX(), goal.getY(), goal.getZ(), opts);
    }

    /** Synchronous variant for tests and tools. */
    public static PathResult findSync(net.minecraft.world.World world, BlockPos start, BlockPos goal, PathOptions opts) {
        var view = new MinecraftWorldView(world);
        return HierarchicalAStar.compute(view,
                start.getX(), start.getY(), start.getZ(),
                goal.getX(), goal.getY(), goal.getZ(), opts);
    }
}
