package com.donut.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;

/**
 * Queue-based rotation controller. Rotations are applied to the player's real
 * client-side yaw/pitch (which vanilla sends in its own movement packets) —
 * no packet-level faking, no silent rotations.
 * <p>
 * Targets may be raw angles or a world position; each task interpolates with a
 * chosen easing over a duration and completes when the target is reached.
 */
public final class RotationManager {
    private static final Deque<Task> queue = new ArrayDeque<>();
    private static Task current;

    public static final class RotationOptions {
        public float durationSeconds = 0.25f;
        public EasingFunctions.Mode easing = EasingFunctions.Mode.EASE_OUT_CUBIC;
        public boolean waitInPlace = false; // freeze movement while rotating

        public static RotationOptions quick() {
            return new RotationOptions();
        }

        public RotationOptions duration(float seconds) {
            this.durationSeconds = seconds;
            return this;
        }

        public RotationOptions easing(EasingFunctions.Mode mode) {
            this.easing = mode;
            return this;
        }
    }

    private static final class Task {
        final Float targetYaw;
        final Float targetPitch;
        final Vec3d lookTarget;
        final RotationOptions opts;
        final CompletableFuture<Void> future = new CompletableFuture<>();
        float startYaw, startPitch;
        long startNanos;

        Task(Float yaw, Float pitch, Vec3d lookTarget, RotationOptions opts) {
            this.targetYaw = yaw;
            this.targetPitch = pitch;
            this.lookTarget = lookTarget;
            this.opts = opts;
        }
    }

    private RotationManager() {
    }

    /** Rotates to absolute angles. */
    public static CompletableFuture<Void> rotateTo(float yaw, float pitch, RotationOptions opts) {
        Task t = new Task(yaw, pitch, null, opts);
        queue.add(t);
        return t.future;
    }

    /** Rotates to look at a world position (computed at dequeue time). */
    public static CompletableFuture<Void> lookAt(Vec3d target, RotationOptions opts) {
        Task t = new Task(null, null, target, opts);
        queue.add(t);
        return t.future;
    }

    public static void clear() {
        queue.clear();
        if (current != null && !current.future.isDone()) current.future.cancel(false);
        current = null;
    }

    public static boolean isBusy() {
        return current != null || !queue.isEmpty();
    }

    /** Called at the start of every client tick. */
    public static void tick(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        if (p == null) {
            clear();
            return;
        }
        if (current == null) {
            current = queue.poll();
            if (current == null) return;
            current.startYaw = p.getYaw();
            current.startPitch = p.getPitch();
            current.startNanos = System.nanoTime();
        }

        float targetYaw, targetPitch;
        if (current.targetYaw != null) {
            targetYaw = current.targetYaw;
            targetPitch = current.targetPitch;
        } else {
            Vec3d eye = p.getEyePos();
            Vec3d delta = current.lookTarget.subtract(eye);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            targetYaw = RotationUtils.yawFromDirection(delta.x, delta.z);
            targetPitch = RotationUtils.pitchFromDirection(delta.x, delta.y, delta.z);
        }

        float elapsed = (System.nanoTime() - current.startNanos) / 1_000_000_000f;
        float progress = elapsed / Math.max(0.01f, current.opts.durationSeconds);
        if (progress >= 1f) {
            p.setYaw(targetYaw);
            p.setPitch(targetPitch);
            current.future.complete(null);
            current = null;
            return;
        }

        float eased = current.opts.easing.apply(progress);
        float yaw = RotationUtils.lerpAngle(current.startYaw, targetYaw, eased);
        float pitch = current.startPitch + (targetPitch - current.startPitch) * eased;
        p.setYaw(yaw);
        p.setPitch(pitch);
    }
}
