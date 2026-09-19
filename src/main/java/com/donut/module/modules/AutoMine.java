package com.donut.module.modules;

import com.donut.mining.DepositPlanner;
import com.donut.mining.VeinMiner;
import com.donut.module.Category;
import com.donut.module.Module;
import com.donut.module.settings.BooleanSetting;
import com.donut.module.settings.EnumSetting;
import com.donut.module.settings.NumberSetting;
import com.donut.module.settings.StringSetting;
import com.donut.pathfinding.MovementInputOverride;
import com.donut.rotation.RotationManager;
import com.donut.rotation.RotationUtils;
import com.donut.schematic.HotbarManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Mines blocks that match a configured target list. Legit-first design: only
 * blocks within reach and visible to the real camera raycast are broken, the
 * camera actually rotates toward each target via {@link RotationManager}, and
 * breaking goes through the vanilla interaction manager. Rate is limited in
 * blocks-per-minute; tool swap picks the fastest hotbar tool for the block.
 *
 * <p><b>Vein mode</b> extends mining to blocks connected to the seeded target
 * (26-way, nearest-first order) — every vein block still passes the same
 * visibility + reach checks and the shared blocks-per-minute budget, with a
 * Gaussian delay variance between breaks.
 *
 * <p><b>Chest deposit</b>: when the inventory is full, walks to the nearest
 * chest in range, opens it through a real block interaction and deposits the
 * configured trash items with vanilla slot clicks. No packet faking anywhere.
 */
public final class AutoMine extends Module {
    public enum Mode { SINGLE, VEIN }

    private enum DepositState { MINING, WALKING, OPENING, DEPOSITING }

    private final StringSetting blocks = new StringSetting("Blocks",
            "diamond_ore, deepslate_diamond_ore", "Comma-separated block ids (minecraft: prefix optional)");
    private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.VEIN,
            "SINGLE mines the targeted block only; VEIN extends to connected same-ore blocks");
    private final NumberSetting maxVeinSize = new NumberSetting("Vein Size", 64, 1, 512, 1, 0,
            "Maximum blocks mined per vein");
    private final NumberSetting blocksPerMinute = new NumberSetting("Speed (BPM)", 120, 10, 600, 5, 0,
            "Blocks per minute (shared by vein blocks, plus delay variance)");
    private final NumberSetting reach = new NumberSetting("Reach", 4.5, 2.0, 5.5, 0.5, 1,
            "Maximum mining distance");
    private final BooleanSetting toolSwap = new BooleanSetting("Tool Swap", true,
            "Select the fastest hotbar tool for the target block");
    private final BooleanSetting dropTrash = new BooleanSetting("Drop Trash", true,
            "Throw configured trash items when the inventory is full");
    private final StringSetting depositItems = new StringSetting("Deposit Items",
            "cobblestone, dirt, gravel, sand, netherrack, diorite, granite, andesite, flint",
            "Comma-separated item ids treated as trash (dropped when full, deposited into chests)");
    private final BooleanSetting chestDeposit = new BooleanSetting("Chest Deposit", true,
            "Walk to a nearby chest and deposit trash items when the inventory is full");
    private final NumberSetting chestRange = new NumberSetting("Chest Range", 8.0, 2.0, 32.0, 0.5, 1,
            "Maximum distance to the deposit chest");

    private final Set<String> targets = new HashSet<>();
    private final Set<String> trashIds = new HashSet<>();
    private final Deque<BlockPos> veinQueue = new ArrayDeque<>();
    private final Random random = new Random();

    private double credit;
    private BlockPos current;
    private int pauseTicks;
    private String status = "idle";

    private DepositState depositState = DepositState.MINING;
    private BlockPos chestPos;
    private int openTicks, depositTicks, depositCooldownTicks, processedStacks, totalToDeposit;

    public AutoMine() {
        super("AutoMine", "Mines configured target blocks you can actually see", Category.PLAYER);
        settings.add(blocks);
        settings.add(mode);
        settings.add(maxVeinSize);
        settings.add(blocksPerMinute);
        settings.add(reach);
        settings.add(toolSwap);
        settings.add(dropTrash);
        settings.add(depositItems);
        settings.add(chestDeposit);
        settings.add(chestRange);
        blocks.onChanged(v -> parseTargets());
        depositItems.onChanged(v -> parseTargets());
    }

    @Override
    protected void init() {
        listenTick();
        parseTargets();
    }

    private void parseTargets() {
        targets.clear();
        for (String part : blocks.get().split(",")) {
            String t = normalize(part);
            if (!t.isEmpty()) targets.add(t);
        }
        trashIds.clear();
        for (String part : depositItems.get().split(",")) {
            String t = normalize(part);
            if (t.isEmpty()) continue;
            trashIds.add(t.contains(":") ? t : "minecraft:" + t);
        }
    }

    private static String normalize(String raw) {
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("minecraft:")) t = t.substring("minecraft:".length());
        return t;
    }

    @Override
    protected void onEnable() {
        credit = 0;
        current = null;
        pauseTicks = 0;
        veinQueue.clear();
        depositState = DepositState.MINING;
        chestPos = null;
        status = "idle";
    }

    @Override
    protected void onDisable() {
        current = null;
        veinQueue.clear();
        if (depositState != DepositState.MINING) {
            MovementInputOverride.end();
            var p = MinecraftClient.getInstance().player;
            if (p != null && p.currentScreenHandler != p.playerScreenHandler) p.closeScreen();
        }
        depositState = DepositState.MINING;
        chestPos = null;
    }

    /** Human-readable status for the HUD, or null when disabled. */
    public String status() {
        return isEnabled() ? status : null;
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (client.world == null || client.interactionManager == null || player == null) return;

        if (depositCooldownTicks > 0) depositCooldownTicks--;

        switch (depositState) {
            case WALKING -> tickWalkToChest(client, player);
            case OPENING -> tickOpenChest(client, player);
            case DEPOSITING -> tickDeposit(client, player);
            case MINING -> tickMine(client, player);
        }
    }

    // ---------------------------------------------------------------- mining

    private void tickMine(MinecraftClient client, ClientPlayerEntity player) {
        if (pauseTicks > 0) {
            pauseTicks--;
            status = "paused " + String.format(Locale.ROOT, "%.1f", pauseTicks / 20.0) + "s";
            return;
        }

        credit += blocksPerMinute.get() / (60.0 * 20.0); // bpm -> per tick
        if (credit < 1.0 && current == null) {
            status = "waiting (rate limit)";
            return;
        }

        if (current != null) {
            if (!isTarget(client.world.getBlockState(current)) || !inReach(player, current)) {
                client.interactionManager.cancelBlockBreaking();
                current = null;
            } else {
                mine(client, player, current);
                if (current != null) return; // still breaking
            }
        }

        if (credit < 1.0) return;
        BlockPos next = nextTarget(client, player);
        if (next == null) {
            status = veinQueue.isEmpty() ? "idle (no targets in reach)" : "vein continues out of reach";
            return;
        }
        credit -= 1.0;
        if (toolSwap.get()) swapBestTool(client, player, client.world.getBlockState(next));
        current = next;
        status = "mining " + blockId(client.world.getBlockState(next)) + veinInfo();
        mine(client, player, current);
    }

    /** Vein queue first (validated), otherwise a fresh visible seed + flood fill. */
    private BlockPos nextTarget(MinecraftClient client, ClientPlayerEntity player) {
        while (!veinQueue.isEmpty()) {
            BlockPos head = veinQueue.peek();
            if (isTarget(client.world.getBlockState(head))) return veinQueue.poll();
            veinQueue.poll(); // stale cell (world changed)
        }
        BlockPos seed = findTarget(client, player);
        if (seed != null && mode.get() == Mode.VEIN) {
            List<int[]> vein = VeinMiner.collect(worldLookup(client),
                    seed.getX(), seed.getY(), seed.getZ(), maxVeinSize.intValue());
            for (int i = 1; i < vein.size(); i++) { // skip the seed itself
                int[] c = vein.get(i);
                veinQueue.add(new BlockPos(c[0], c[1], c[2]));
            }
        }
        return seed;
    }

    private VeinMiner.BlockLookup worldLookup(MinecraftClient client) {
        return (x, y, z) -> {
            if (client.world == null) return null;
            BlockState s = client.world.getBlockState(new BlockPos(x, y, z));
            return s.isAir() ? null : blockId(s);
        };
    }

    /** Gaussian per-block delay around the BPM pacing, clamped to >= 1 tick. */
    private int veinDelayTicks() {
        double base = 60.0 * 20.0 / Math.max(1.0, blocksPerMinute.get());
        double jitter = base * 0.35 * random.nextGaussian();
        return Math.max(1, (int) Math.round(base + jitter));
    }

    private String veinInfo() {
        return veinQueue.isEmpty() ? "" : " [" + (veinQueue.size() + 1) + " in vein]";
    }

    private BlockPos findTarget(MinecraftClient client, ClientPlayerEntity player) {
        double r = reach.get();
        double rSq = r * r;
        Vec3d eye = player.getEyePos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int cr = (int) Math.ceil(r);
        BlockPos base = player.getBlockPos();
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dy = -cr; dy <= cr; dy++) {
                for (int dz = -cr; dz <= cr; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    if (!isTarget(client.world.getBlockState(pos))) continue;
                    double dist = eye.squaredDistanceTo(Vec3d.ofCenter(pos));
                    if (dist > rSq || dist >= bestDist) continue;
                    if (!visible(client, player, pos)) continue;
                    best = pos;
                    bestDist = dist;
                }
            }
        }
        return best;
    }

    /** True only when the real camera raycast hits this exact block. */
    private boolean visible(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
        HitResult hit = player.raycast(reach.get() + 0.5, 1.0f, false);
        return hit instanceof BlockHitResult bhr
                && bhr.getType() == HitResult.Type.BLOCK
                && bhr.getBlockPos().equals(pos);
    }

    private boolean inReach(ClientPlayerEntity player, BlockPos pos) {
        return player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= reach.get() * reach.get();
    }

    private boolean isTarget(BlockState state) {
        return targets.contains(blockId(state));
    }

    private static String blockId(BlockState state) {
        String id = Registries.BLOCK.getId(state.getBlock()).toString();
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private void mine(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
        HitResult hit = player.raycast(reach.get() + 0.5, 1.0f, false);
        if (!(hit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(pos)) {
            // Aim at it first; retry when the camera is roughly aligned.
            if (!RotationManager.isBusy()) {
                RotationManager.lookAt(Vec3d.ofCenter(pos),
                        RotationManager.RotationOptions.quick().duration(0.15f));
            }
            return;
        }

        Vec3d center = Vec3d.ofCenter(pos);
        float wantYaw = RotationUtils.yawFromDirection(center.x - player.getX(), center.z - player.getZ());
        float wantPitch = RotationUtils.pitchFromDirection(center.x - player.getX(),
                center.y - player.getEyePos().y, center.z - player.getZ());
        if (!RotationUtils.withinRange(player.getYaw(), player.getPitch(), wantYaw, wantPitch, 20f)) {
            if (!RotationManager.isBusy()) {
                RotationManager.lookAt(center, RotationManager.RotationOptions.quick().duration(0.15f));
            }
            return;
        }

        BlockState state = client.world.getBlockState(pos);
        if (state.isAir()) {
            credit = Math.max(0, credit - 0.5);
            current = null;
            return;
        }

        if (client.interactionManager.getCurrentGameMode() == GameMode.CREATIVE) {
            client.interactionManager.attackBlock(pos, bhr.getSide());
            player.swingHand(Hand.MAIN_HAND);
            onBlockBroken(client, player);
        } else {
            boolean continuing = client.interactionManager.updateBlockBreakingProgress(pos, bhr.getSide());
            player.swingHand(Hand.MAIN_HAND);
            if (!continuing || client.world.getBlockState(pos).isAir()) {
                onBlockBroken(client, player);
            }
        }
    }

    private void onBlockBroken(MinecraftClient client, ClientPlayerEntity player) {
        current = null;
        if (mode.get() == Mode.VEIN && !veinQueue.isEmpty()) {
            current = veinQueue.poll();
            pauseTicks = veinDelayTicks();
            status = "mining " + blockId(client.world.getBlockState(current)) + veinInfo();
        } else {
            pauseTicks = 4; // small natural pause between blocks
        }
        maybeDropTrash(client, player);
    }

    private void swapBestTool(MinecraftClient client, ClientPlayerEntity player, BlockState state) {
        int best = -1;
        float bestSpeed = 1.0f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getItem().getMiningSpeed(stack, state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        if (best >= 0) player.getInventory().selectedSlot = best;
    }

    // ------------------------------------------------------- trash + deposit

    private List<DepositPlanner.StackView> inventoryViews(ClientPlayerEntity player) {
        List<DepositPlanner.StackView> views = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            ItemStack st = player.getInventory().getStack(i);
            String id = st.isEmpty() ? "" : Registries.ITEM.getId(st.getItem()).toString();
            int count = st.getCount();
            views.add(new DepositPlanner.StackView() {
                @Override public String itemId() { return id; }
                @Override public int count() { return count; }
            });
        }
        return views;
    }

    /** Drops one trash stack via the vanilla drop key path when full. */
    private void maybeDropTrash(MinecraftClient client, ClientPlayerEntity player) {
        if (!dropTrash.get() || player.getInventory().getEmptySlot() != -1) return;
        int[] droppable = DepositPlanner.selectForDeposit(inventoryViews(player), trashIds);
        if (droppable.length > 0) {
            player.getInventory().selectedSlot = droppable[0];
            player.dropSelectedItem(true);
        }
        if (player.getInventory().getEmptySlot() == -1
                && chestDeposit.get() && depositCooldownTicks == 0) {
            findChestAndStart(client, player);
        }
    }

    private void findChestAndStart(MinecraftClient client, ClientPlayerEntity player) {
        double r = chestRange.get();
        double rSq = r * r;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int cr = (int) Math.ceil(r);
        BlockPos base = player.getBlockPos();
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dy = -cr; dy <= cr; dy++) {
                for (int dz = -cr; dz <= cr; dz++) {
                    BlockPos p = base.add(dx, dy, dz);
                    if (!(client.world.getBlockState(p).getBlock() instanceof ChestBlock)) continue;
                    double d = player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(p));
                    if (d <= rSq && d < bestD) {
                        best = p;
                        bestD = d;
                    }
                }
            }
        }
        if (best == null) {
            status = "inventory full — no chest in range";
            depositCooldownTicks = 100; // retry in ~5s instead of every tick
            return;
        }
        chestPos = best;
        depositState = DepositState.WALKING;
        client.interactionManager.cancelBlockBreaking();
        MovementInputOverride.begin();
        status = "walking to chest at " + best.toShortString();
    }

    private void tickWalkToChest(MinecraftClient client, ClientPlayerEntity player) {
        if (chestPos == null || !(client.world.getBlockState(chestPos).getBlock() instanceof ChestBlock)) {
            MovementInputOverride.end();
            depositState = DepositState.MINING;
            return;
        }
        Vec3d center = Vec3d.ofCenter(chestPos);
        double distSq = player.getEyePos().squaredDistanceTo(center);
        if (distSq <= 16.0) { // within 4 blocks: stop and open
            MovementInputOverride.end();
            depositState = DepositState.OPENING;
            openTicks = 0;
            return;
        }
        steerToward(client, player, center);
        status = "walking to chest (" + (int) Math.sqrt(distSq) + "m)";
    }

    /** Very simple steering: face the target and press forward; jump for steps. */
    private void steerToward(MinecraftClient client, ClientPlayerEntity player, Vec3d target) {
        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz > 1.0) {
            float wantYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            float dyaw = RotationUtils.delta(player.getYaw(), wantYaw);
            player.setYaw(player.getYaw() + RotationUtils.wrapDegrees(dyaw * 0.3f));
            boolean jump = client.world.getBlockState(player.getBlockPos().up()).isReplaceable()
                    && client.world.getBlockState(player.getBlockPos().up(2)).isReplaceable()
                    && client.world.getBlockState(player.getBlockPos().down())
                            .isSolidBlock(client.world, player.getBlockPos());
            MovementInputOverride.set(1f, 0f, jump, false, false);
        }
    }

    private void tickOpenChest(MinecraftClient client, ClientPlayerEntity player) {
        if (chestPos == null || !(client.world.getBlockState(chestPos).getBlock() instanceof ChestBlock)) {
            depositState = DepositState.MINING;
            return;
        }
        if (client.currentScreen instanceof HandledScreen<?>) {
            depositState = DepositState.DEPOSITING;
            depositTicks = 0;
            processedStacks = 0;
            totalToDeposit = DepositPlanner.selectForDeposit(inventoryViews(player), trashIds).length;
            status = "depositing into chest";
            return;
        }
        if (++openTicks > 40) { // chest refused / obstructed
            status = "could not open chest";
            depositCooldownTicks = 200;
            depositState = DepositState.MINING;
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
            // Screen closed underneath us (server deny, keypress, ...) — bail out.
            MovementInputOverride.end();
            depositState = DepositState.MINING;
            return;
        }
        if (++depositTicks > 100) {
            finishDeposit(player, "deposit timed out");
            return;
        }

        int syncId = player.currentScreenHandler.syncId;
        int[] slots = DepositPlanner.selectForDeposit(inventoryViews(player), trashIds);
        if (slots.length == 0) {
            finishDeposit(player, "deposit complete");
            return;
        }

        int budget = 4; // slot clicks per tick
        for (int invSlot : slots) {
            if (budget-- <= 0) break;
            ItemStack stack = player.getInventory().getStack(invSlot);
            if (stack.isEmpty()) continue;

            int handlerSlot = invSlot <= 8 ? 36 + invSlot : invSlot;
            int chestSlot = findEmptyChestSlot(player);
            if (chestSlot >= 0) {
                client.interactionManager.clickSlot(syncId, handlerSlot, 0, SlotActionType.PICKUP, player);
                client.interactionManager.clickSlot(syncId, chestSlot, 0, SlotActionType.PICKUP, player);
            } else if (invSlot > 8) {
                int hotbar = Math.floorMod(player.getInventory().selectedSlot, 9);
                client.interactionManager.clickSlot(syncId, handlerSlot, hotbar, SlotActionType.SWAP, player);
                int chestSlot2 = findEmptyChestSlot(player);
                if (chestSlot2 < 0) {
                    finishDeposit(player, "chest full");
                    return;
                }
                client.interactionManager.clickSlot(syncId, 36 + hotbar, 0, SlotActionType.PICKUP, player);
                client.interactionManager.clickSlot(syncId, chestSlot2, 0, SlotActionType.PICKUP, player);
            } else {
                finishDeposit(player, "chest full");
                return;
            }
            processedStacks++;
            status = "depositing " + processedStacks + "/" + totalToDeposit;
        }
    }

    /** First empty handler slot that is not part of the player inventory. */
    private static int findEmptyChestSlot(ClientPlayerEntity player) {
        List<Slot> slots = player.currentScreenHandler.slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.inventory == player.getInventory()) continue;
            if (slot.getStack().isEmpty()) return i;
        }
        return -1;
    }

    private void finishDeposit(ClientPlayerEntity player, String note) {
        player.closeScreen();
        MovementInputOverride.end();
        depositState = DepositState.MINING;
        depositCooldownTicks = 100; // don't re-trigger immediately
        status = note;
    }
}
