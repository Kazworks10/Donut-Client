package com.donut.pathfinding;

import java.util.concurrent.Executor;

/**
 * Options for a path search and its execution.
 */
public final class PathOptions {
    /** Maximum acceptable accumulated danger along the path (0..~inf). */
    public final float maxDanger;
    /** Prefer routes adjacent to walls/cover (mild cost shaping). */
    public final boolean preferCover;
    /** Allow jump gaps and other parkour moves. */
    public final boolean parkour;
    /** Node budget for the block-level search. */
    public final int maxNodes;
    /** Hard search timeout in milliseconds. */
    public final long timeoutMs;
    /** Maximum fall distance accepted in one move. */
    public final int maxFall;
    /** Stop this many blocks from the goal instead of exactly on it. */
    public final double goalRadius;
    /** Executor for the async search (virtual threads recommended). */
    public final Executor executor;

    private PathOptions(Builder b) {
        this.maxDanger = b.maxDanger;
        this.preferCover = b.preferCover;
        this.parkour = b.parkour;
        this.maxNodes = b.maxNodes;
        this.timeoutMs = b.timeoutMs;
        this.maxFall = b.maxFall;
        this.goalRadius = b.goalRadius;
        this.executor = b.executor;
    }

    public static Builder defaults() {
        return new Builder();
    }

    public static final class Builder {
        private float maxDanger = 0.3f;
        private boolean preferCover = true;
        private boolean parkour = true;
        private int maxNodes = 200_000;
        private long timeoutMs = 30_000;
        private int maxFall = 3;
        private double goalRadius = 0.0;
        private Executor executor = java.util.concurrent.ForkJoinPool.commonPool();

        public Builder maxDanger(float v) {
            this.maxDanger = v;
            return this;
        }

        public Builder preferCover(boolean v) {
            this.preferCover = v;
            return this;
        }

        public Builder parkour(boolean v) {
            this.parkour = v;
            return this;
        }

        public Builder maxNodes(int v) {
            this.maxNodes = v;
            return this;
        }

        public Builder timeoutMs(long v) {
            this.timeoutMs = v;
            return this;
        }

        public Builder maxFall(int v) {
            this.maxFall = v;
            return this;
        }

        public Builder goalRadius(double v) {
            this.goalRadius = v;
            return this;
        }

        public Builder executor(Executor e) {
            this.executor = e;
            return this;
        }

        public PathOptions build() {
            return new PathOptions(this);
        }
    }
}
