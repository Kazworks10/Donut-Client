package com.donut.mining;

import com.donut.pathfinding.MovementInputOverride;
import com.donut.pathfinding.PathExecutor;
import com.donut.pathfinding.PathOptions;
import com.donut.pathfinding.PathResult;
import com.donut.pathfinding.Pathfinder;
import com.donut.rotation.RotationManager;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Owns the chest-deposit mechanics: WALKING (routed via {@link Pathfinder},
 * straight-steer fallback per {@link DepositRouting}, stuck watchdog),
 * OPENING (real block interaction, camera turns via {@link RotationManager})
 * and DEPOSITING (QUICK_MOVE shift-clicks with a per-tick budget).
 * <p>
 * Policy — when to deposit and what counts as trash — stays with the owning
 * module via {@link Deps}. All interactions are vanilla; nothing faked.
 */
public final class ChestDepositController {

    /** Policy surface implemented by the owning module. */
    public interface Deps {
        /** Registry ids of depositable items (namespaced form). */
        java.util.Set<String> depositIds();

        /** True while the flow should keep running for the given chest. */
        boolean keepRunning(BlockPos chestPos);
    }

    // Tuning
    public static final double STRAIGHT_STEER_MAX = 10.0;   // blocks: steer fallback range
    public static final long WALK_STUCK_TIMEOUT_MS = 3000;  // no-progress window
    public static final double WALK_STUCK_DELTA_SQ = 0.0025; // ~5cm per tick counts as stuck
    public static final int CHEST_FULL_FREEZE_TICKS = 5;    // unchanged trash total => chest full
    public static final int OPEN_TIMEOUT_TICKS = 40;
    public static final int DEPOSIT_TIMEOUT_TICKS = 100;
    public static final int COOLDOWN_TICKS = 100;
    public static final int CLICKS_PER_TICK = 4;
    public static final double OPEN_RANGE_SQ = 16.0;        // within 4 blocks: stop and open

    public enum State { MINING, WALKING, OPENING, DEPOSITING }

    private final Deps deps;

    private State state = State.MINING;
    private BlockPos chestPos;
    private Vec3d chestCenter;
    private PathExecutor executor;
    private CompletableFuture<PathResult> pending;
    private boolean steerFallback;
    private Vec3d lastWalkPos;
    private long noProgressSinceMs = -1;
    private int openTicks, depositTicks, cooldownTicks;
    private int processedStacks, totalToDeposit, lastTrashTotal, frozenTicks;
    private String status = "";

    public ChestDepositController(Deps deps) {
        this.deps = deps;
    }

    /** Tick from the owning module every client tick (must be on the client thread). */
    public void tick(MinecraftClient client, ClientPlayerEntity player) {
        if (cooldownTicks > 0) cooldownTicks--;
        switch (state) {
            case WALKING -> tickWalk(client, player);
            case OPENING -> tickOpen(client, player);
            case DEPOSITING -> tickDeposit(client, player);
            case MINING -> { /* idle */ }
        }
    }

    public boolean busy() {
        return state != State.MINING;
    }

    /** True while the flow is cooling down and should not be re-triggered. */
    public boolean coolingDown() {
        return cooldownTicks > 0;
    }

    public State state() {
        return state;
    }

    /** One-line progress report for the HUD; empty when idle. */
    public String status() {
        return status;
    }

    /** Starts walking to the given chest (real path search, async). */
    public void start(MinecraftClient client, ClientPlayerEntity player, BlockPos chest) {
        chestPos = chest;
        chestCenter = Vec3d.ofCenter(chest);
        state = State.WALKING;
        client.interactionManager.cancelBlockBreaking();
        MovementInputOverride.begin();
        lastWalkPos = null;
        noProgressSinceMs = -1;
        steerFallback = false;
        planPath(client, chestCenter);
        status = "walking to chest at " + chest.toShortString();
    }

    /** Stops everything and returns to idle. Never closes the chest screen itself. */
    public void stop() {
        if (executor != null) executor.stop();
        MovementInputOverride.end();
        state = State.MINING;
        chestPos = null;
        status = "";
    }

    private void abort(String note) {
        stop();
        cooldownTicks = COOLDOWN_TICKS;
        status = note;
    }

    private void abortClosingScreen(MinecraftClient client, String note) {
        client.player.closeScreen();
        abort(note);
    }

    private void planPath(MinecraftClient client, Vec3d to) {
        if (client.player == null || client.world == null) return;
        pending = Pathfinder.goTo(client.world, client.player.getPos(), to,
                PathOptions.defaults().parkour(true).timeoutMs(5_000).maxNodes(60_000).build());
        pending.thenAccept(result -> client.execute(() -> {
            if (state != State.WALKING || chestPos == null) return;
            if (result.success()) {
                steerFallback = false;
                executor().follow(result.waypoints(), chestCenter);
            } else {
                double dist = Math.sqrt(client.player.getEyePos().squaredDistanceTo(chestCenter));
                switch (DepositRouting.decide(false, dist, STRAIGHT_STEER_MAX)) {
                    case STEER_STRAIGHT -> {
                        steerFallback = true;
                        if (executor != null) executor.stop();
                        MovementInputOverride.begin();
                    }
                    default -> abort("no path to chest");
                }
            }
        }));
    }

    private PathExecutor executor() {
        if (executor == null) {
            executor = new PathExecutor(WALK_STUCK_TIMEOUT_MS);
            executor.replanHook = from -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (chestCenter != null) planPath(client, chestCenter);
            };
        }
        return executor;
    }

    private void tickWalk(MinecraftClient client, ClientPlayerEntity player) {
        if (chestPos == null || !deps.keepRunning(chestPos)
                || !(client.world.getBlockState(chestPos).getBlock() instanceof ChestBlock)) {
            abort("chest gone");
            return;
        }
        Vec3d center = Vec3d.ofCenter(chestPos);
        double distSq = player.getEyePos().squaredDistanceTo(center);
        if (distSq <= OPEN_RANGE_SQ) {
            if (executor != null) executor.stop();
            MovementInputOverride.end();
            state = State.OPENING;
            openTicks = 0;
            return;
        }

        // Stuck watchdog (covers executor-driven and straight-fallback walking)
        Vec3d pos = player.getPos();
        if (lastWalkPos != null) {
            if (pos.squaredDistanceTo(lastWalkPos.x, lastWalkPos.y, lastWalkPos.z) < WALK_STUCK_DELTA_SQ) {
                if (noProgressSinceMs < 0) noProgressSinceMs = System.currentTimeMillis();
                else if (System.currentTimeMillis() - noProgressSinceMs > WALK_STUCK_TIMEOUT_MS) {
                    abort("stuck walking to chest");
                    return;
                }
            } else {
                noProgressSinceMs = -1;
            }
        }
        lastWalkPos = pos;

        if (steerFallback) {
            MovementSteering.steerToward(client, player, center);
            status = "walking to chest (direct, " + (int) Math.sqrt(distSq) + "m)";
        } else if (executor != null) {
            executor.tick(client);
            status = "walking to chest (" + (int) Math.sqrt(distSq) + "m)";
        }
    }

    private void tickOpen(MinecraftClient client, ClientPlayerEntity player) {
        if (chestPos == null || !deps.keepRunning(chestPos)
                || !(client.world.getBlockState(chestPos).getBlock() instanceof ChestBlock)) {
            abort("chest gone");
            return;
        }
        if (client.currentScreen instanceof HandledScreen<?>) {
            state = State.DEPOSITING;
            depositTicks = 0;
            processedStacks = 0;
            frozenTicks = 0;
            lastTrashTotal = trashTotal(player);
            totalToDeposit = DepositPlanner.selectForDeposit(inventoryViews(player), deps.depositIds()).length;
            status = "depositing into chest";
            return;
        }
        if (++openTicks > OPEN_TIMEOUT_TICKS) {
            abort("could not open chest");
            return;
        }
        Vec3d center = Vec3d.ofCenter(chestPos);
        RotationManager.lookAt(center, RotationManager.RotationOptions.quick().duration(0.1f));
        Direction side = center.y > player.getEyePos().y ? Direction.UP : Direction.DOWN;
        Vec3d hitPos = center.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, side, chestPos, false);
        if (client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit).isAccepted()) {
            player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void tickDeposit(MinecraftClient client, ClientPlayerEntity player) {
        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            abort("deposit screen closed");
            return;
        }
        if (++depositTicks > DEPOSIT_TIMEOUT_TICKS) {
            player.closeScreen();
            abort("deposit timed out");
            return;
        }

        int syncId = player.currentScreenHandler.syncId;
        int[] slots = DepositPlanner.selectForDeposit(inventoryViews(player), deps.depositIds());
        if (slots.length == 0) {
            player.closeScreen();
            abort("deposit complete");
            return;
        }

        int budget = CLICKS_PER_TICK;
        for (int invSlot : slots) {
            if (budget-- <= 0) break;
            if (player.getInventory().getStack(invSlot).isEmpty()) continue;

            // PlayerScreenHandler: hotbar is handler slots 36..44, main 9..35.
            int handlerSlot = invSlot <= 8 ? 36 + invSlot : invSlot;
            Slot slot = player.currentScreenHandler.slots.get(handlerSlot);
            if (slot == null || slot.inventory != player.getInventory()) {
                player.closeScreen();
                abort("unexpected screen layout");
                return;
            }
            client.interactionManager.clickSlot(syncId, handlerSlot, 0, SlotActionType.QUICK_MOVE, player);
            processedStacks++;
            status = "depositing " + processedStacks + "/" + totalToDeposit;
        }

        int totalNow = trashTotal(player);
        if (totalNow > 0 && totalNow == lastTrashTotal) {
            if (++frozenTicks >= CHEST_FULL_FREEZE_TICKS) {
                player.closeScreen();
                abort("chest full — " + processedStacks + " stacks deposited");
                return;
            }
        } else {
            frozenTicks = 0;
        }
        lastTrashTotal = totalNow;
    }

    private List<DepositPlanner.StackView> inventoryViews(ClientPlayerEntity player) {
        List<DepositPlanner.StackView> views = new java.util.ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            net.minecraft.item.ItemStack st = player.getInventory().getStack(i);
            String id = st.isEmpty() ? "" : Registries.ITEM.getId(st.getItem()).toString();
            int count = st.getCount();
            views.add(new DepositPlanner.StackView() {
                @Override public String itemId() { return id; }
                @Override public int count() { return count; }
            });
        }
        return views;
    }

    private int trashTotal(ClientPlayerEntity player) {
        int total = 0;
        for (int slot : DepositPlanner.selectForDeposit(inventoryViews(player), deps.depositIds())) {
            total += player.getInventory().getStack(slot).getCount();
        }
        return total;
    }
}
