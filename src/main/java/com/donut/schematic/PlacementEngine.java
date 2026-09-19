package com.donut.schematic;

import com.donut.rotation.RotationManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Places schematic blocks through the vanilla interaction path (right-click
 * against a supporting neighbor, exactly like a player). Per tick it places at
 * most one block and accumulates fractional credits from the configured
 * blocks-per-minute rate; targets require line-of-reach support on one of the
 * six faces, otherwise they are deferred (no scaffolding is generated).
 */
public final class PlacementEngine {
    private List<int[]> plan; // [x, y, z, globalId]
    private BlockPalette palette;
    private int index;
    private double placementCredit;
    private int placedCount;
    private int deferred; // consecutive unsupported skips

    public void begin(List<int[]> plan, BlockPalette palette) {
        this.plan = plan;
        this.palette = palette;
        this.index = 0;
        this.placedCount = 0;
        this.deferred = 0;
        this.placementCredit = 0;
    }

    public boolean isDone() {
        return plan == null || index >= plan.size();
    }

    public int index() {
        return index;
    }

    public int total() {
        return plan == null ? 0 : plan.size();
    }

    public int placedCount() {
        return placedCount;
    }

    public void setIndex(int i) {
        this.index = i;
    }

    public List<int[]> plan() {
        return plan;
    }

    public void stop() {
        plan = null;
    }

    /**
     * Places up to the rate limit for this tick. Returns true if anything was
     * placed (used to defer autosave writes to activity).
     */
    public boolean tick(MinecraftClient client, float blocksPerMinute, BlockPos origin) {
        if (isDone() || client.player == null || client.world == null || client.interactionManager == null) {
            return false;
        }
        placementCredit += blocksPerMinute / (60.0 * 20.0); // bpm -> per-tick
        boolean placed = false;
        int guard = 0;
        while (placementCredit >= 1.0 && !isDone() && guard++ < 8) {
            int[] cell = plan.get(index);
            if (tryPlace(client, cell, origin)) {
                placementCredit -= 1.0;
                index++;
                placedCount++;
                deferred = 0;
                placed = true;
            } else {
                placementCredit = Math.min(placementCredit, 1.0);
                deferred++;
                // Stuck on an unsupported/obstructed cell: defer it to the back later;
                // for now stop this tick to avoid hammering the server.
                break;
            }
        }
        return placed;
    }

    private boolean tryPlace(MinecraftClient client, int[] cell, BlockPos origin) {
        ClientPlayerEntity player = client.player;
        BlockPos target = origin.add(cell[0], cell[1], cell[2]);
        BlockState existing = client.world.getBlockState(target);

        // Already correct (or occupied by the same block): count as done.
        String want = palette != null ? palette.name(cell[3]) : null;
        if (matchesBlock(existing, want)) {
            return true;
        }
        if (!existing.isReplaceable()) {
            // Occupied by something else; skip permanently rather than grief the area.
            return true;
        }

        // Find a supporting neighbor to click against (legit placement path).
        Direction side = null;
        BlockPos support = null;
        for (Direction d : Direction.values()) {
            BlockPos n = target.offset(d);
            BlockState ns = client.world.getBlockState(n);
            if (ns.isReplaceable() || !ns.isSideSolidFullSquare(client.world, n, d.getOpposite())) {
                continue;
            }
            if (ns.getFluidState().isEmpty()) {
                side = d.getOpposite(); // face of the support block that touches target
                support = n;
                break;
            }
        }
        if (support == null) {
            return false; // no reachable support yet; defer
        }

        String blockName = want;
        if (blockName == null || !HotbarManager.ensureSelected(client, blockName)) {
            return false; // out of material; caller can pause
        }

        // Aim at the placement point (real camera rotation via the queue).
        Vec3d aim = Vec3d.ofCenter(target);
        RotationManager.lookAt(aim, RotationManager.RotationOptions.quick().duration(0.1f));

        Vec3d hitPos = Vec3d.ofCenter(support).add(
                side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, side, support, false);
        var result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    /** Whether the world state at pos already equals the wanted block id. */
    public static boolean matchesBlock(BlockState state, String blockName) {
        if (blockName == null) return false;
        var id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
        return id.toString().equals(stripState(blockName));
    }

    static String stripState(String name) {
        int b = name.indexOf('[');
        return b >= 0 ? name.substring(0, b) : name;
    }
}
