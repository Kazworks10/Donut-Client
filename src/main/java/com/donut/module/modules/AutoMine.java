package com.donut.module.modules;

import com.donut.mining.ChestDepositController;
import com.donut.mining.DepositPlanner;
import com.donut.mining.VeinMiner;
import com.donut.module.Category;
import com.donut.module.Module;
import com.donut.module.settings.BooleanSetting;
import com.donut.module.settings.EnumSetting;
import com.donut.module.settings.NumberSetting;
import com.donut.module.settings.StringSetting;
import com.donut.rotation.RotationManager;
import com.donut.rotation.RotationUtils;
import com.donut.util.Pos3Key;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
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
 * Policy module for automated mining: decides what counts as a target, when to
 * mine it and when the inventory needs emptying. Mechanics live in
 * {@link VeinMiner} (flood fill + retry budget) and
 * {@link ChestDepositController} (walk/open/deposit choreography).
 *
 * <p>Legit-first design: only blocks within reach and visible to the real
 * camera raycast are broken, the camera actually rotates toward each target
 * via {@link RotationManager}, and breaking goes through the vanilla
 * interaction manager. Rate is limited in blocks-per-minute with a Gaussian
 * delay variance between vein breaks.
 */
public final class AutoMine extends Module {
    public enum Mode { SINGLE, VEIN }

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
    private final VeinMiner.RetryBudget retryBudget =
            new VeinMiner.RetryBudget(VeinMiner.DEFAULT_MAX_ATTEMPTS);
    private final ChestDepositController deposit = new ChestDepositController(
            new ChestDepositController.Deps() {
                @Override public Set<String> depositIds() {
                    return trashIds;
                }

                @Override public boolean keepRunning(BlockPos chestPos) {
                    return AutoMine.this.isEnabled();
                }
            });
    private final Random random = new Random();

    private double credit;
    private BlockPos current;
    private int pauseTicks;
    private int chestScanCooldown;
    private String status = "idle";

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
        chestScanCooldown = 0;
        veinQueue.clear();
        retryBudget.reset();
        status = "idle";
    }

    @Override
    protected void onDisable() {
        current = null;
        veinQueue.clear();
        retryBudget.reset();
        deposit.stop();
        var p = MinecraftClient.getInstance().player;
        if (p != null && p.currentScreenHandler != p.playerScreenHandler) p.closeScreen();
    }

    /** HUD line while actively doing something; null when enabled but idle. */
    @Override
    public String statusLine() {
        if (!isEnabled()) return null;
        if (deposit.busy()) return deposit.status();
        if (status.equals("idle") || status.startsWith("idle (")
                || status.equals("waiting (rate limit)")) {
            return null;
        }
        return status;
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (client.world == null || client.interactionManager == null || player == null) return;
        if (chestScanCooldown > 0) chestScanCooldown--;

        deposit.tick(client, player);
        if (deposit.busy()) {
            status = deposit.status();
            return;
        }
        tickMine(client, player);
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

    /**
     * Vein queue first (validated against the retry budget), otherwise a fresh
     * visible seed + flood fill. Queue heads that exhausted their validation
     * attempts are skipped so an occluded block cannot freeze the vein.
     */
    private BlockPos nextTarget(MinecraftClient client, ClientPlayerEntity player) {
        while (!veinQueue.isEmpty()) {
            BlockPos head = veinQueue.peek();
            if (retryBudget.failuresOf(Pos3Key.pack(head.getX(), head.getY(), head.getZ()))
                    >= VeinMiner.DEFAULT_MAX_ATTEMPTS) {
                veinQueue.poll(); // hopeless: skip, vein continues
                continue;
            }
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
            // Aim at it first; count a bounded failure only while not mid-turn.
            if (!RotationManager.isBusy()) {
                if (retryBudget.fail(Pos3Key.pack(pos.getX(), pos.getY(), pos.getZ()))) {
                    current = null; // abandon: skip budget exhausted, vein continues
                    return;
                }
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
                if (retryBudget.fail(Pos3Key.pack(pos.getX(), pos.getY(), pos.getZ()))) {
                    current = null;
                    return;
                }
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
        retryBudget.clear(Pos3Key.pack(current.getX(), current.getY(), current.getZ()));
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
                && chestDeposit.get() && !deposit.busy()
                && !deposit.coolingDown() && chestScanCooldown == 0) {
            BlockPos chest = nearestChest(client, player);
            if (chest != null) {
                chestScanCooldown = ChestDepositController.COOLDOWN_TICKS;
                deposit.start(client, player, chest);
            } else {
                status = "inventory full — no chest in range";
                chestScanCooldown = ChestDepositController.COOLDOWN_TICKS;
            }
        }
    }

    private BlockPos nearestChest(MinecraftClient client, ClientPlayerEntity player) {
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
        return best;
    }
}
