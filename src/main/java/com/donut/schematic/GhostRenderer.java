package com.donut.schematic;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Renders pending schematic placements as translucent line boxes ("ghost
 * blocks") so the player can see what the builder will place and where.
 * The current target glows green; pending cells are purple with a slow pulse.
 * <p>
 * Rendering hooks into Fabric's AFTER_TRANSLUCENT world event and always builds
 * its own camera-relative {@link MatrixStack} (the context stack may be null at
 * that stage). State is published by {@link com.donut.module.modules.SchematicBuilder}
 * via {@link #update(List, int, BlockPos)}; reads are volatile so the render
 * thread never sees a torn snapshot.
 */
public final class GhostRenderer {
    private static final int MAX_GHOSTS = 2048;   // hard cap per frame
    private static final int SCAN_LIMIT = 200_000; // entries scanned per frame
    private static final double MAX_RANGE_SQ = 64 * 64;

    private static final float[] CURRENT = {0.30f, 0.95f, 0.45f};
    private static final float[] PENDING = {0.60f, 0.40f, 0.95f};

    private static volatile List<int[]> plan; // [x, y, z, globalId] or null
    private static volatile int index;
    private static volatile BlockPos origin;

    private GhostRenderer() {
    }

    public static void initialize() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(GhostRenderer::render);
    }

    /** Publishes the live build state for rendering (cheap, call every tick). */
    public static void update(List<int[]> newPlan, int newIndex, BlockPos newOrigin) {
        plan = newPlan;
        index = newIndex;
        origin = newOrigin;
    }

    public static void clear() {
        plan = null;
        origin = null;
    }

    private static void render(WorldRenderContext ctx) {
        List<int[]> p = plan;
        BlockPos org = origin;
        if (p == null || org == null || ctx.consumers() == null) return;

        MatrixStack ms = new MatrixStack();
        Vec3d cam = ctx.camera().getPos();
        ms.translate(-cam.x, -cam.y, -cam.z);

        VertexConsumer lines = ctx.consumers().getBuffer(RenderLayer.getLines());
        float pulse = 0.55f + 0.20f * (float) Math.sin(System.currentTimeMillis() * 0.004);

        int drawn = 0;
        int scanned = 0;
        final int size = p.size();
        for (int k = index; k < size && drawn < MAX_GHOSTS && scanned < SCAN_LIMIT; k++, scanned++) {
            int[] c = p.get(k);
            double x = org.getX() + c[0];
            double y = org.getY() + c[1];
            double z = org.getZ() + c[2];
            double dx = x + 0.5 - cam.x;
            double dy = y + 0.5 - cam.y;
            double dz = z + 0.5 - cam.z;
            if (dx * dx + dy * dy + dz * dz > MAX_RANGE_SQ) continue;

            float[] col = (k == index) ? CURRENT : PENDING;
            float a = (k == index) ? 0.9f : pulse;
            WorldRenderer.drawBox(ms, lines, new Box(x, y, z, x + 1, y + 1, z + 1),
                    col[0], col[1], col[2], a);
            drawn++;
        }
    }
}
