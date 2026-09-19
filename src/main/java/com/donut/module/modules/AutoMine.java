package com.donut.module.modules;

import com.donut.module.Category;
import com.donut.module.Module;
import com.donut.module.settings.BooleanSetting;
import com.donut.module.settings.NumberSetting;
import com.donut.module.settings.StringSetting;
import com.donut.rotation.RotationManager;
import com.donut.rotation.RotationUtils;
import com.donut.schematic.HotbarManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Mines blocks that match a configured target list. Legit-first design: only
 * blocks within reach and visible to the real camera raycast are broken, the
 * camera actually rotates toward each target via {@link RotationManager}, and
 * breaking goes through the vanilla interaction manager. Rate is limited in
 * blocks-per-minute; tool swap picks the fastest hotbar tool for the block.
 */
public final class AutoMine extends Module {
    private final StringSetting blocks = new StringSetting("Blocks",
            "diamond_ore, deepslate_diamond_ore", "Comma-separated block ids (minecraft: prefix optional)");
    private final NumberSetting blocksPerMinute = new NumberSetting("Speed (BPM)", 120, 10, 600, 5, 0,
            "Blocks per minute");
    private final NumberSetting reach = new NumberSetting("Reach", 4.5, 2.0, 5.5, 0.5, 1,
            "Maximum mining distance");
    private final BooleanSetting toolSwap = new BooleanSetting("Tool Swap", true,
            "Select the fastest hotbar tool for the target block");
    private final BooleanSetting dropTrash = new BooleanSetting("Drop Trash", true,
            "Throw common filler blocks when the inventory is full");

    private final Set<String> targets = new HashSet<>();
    private double credit;
    private BlockPos current;
    private int pauseTicks;

    public AutoMine() {
        super("AutoMine", "Mines configured target blocks you can actually see", Category.PLAYER);
        settings.add(blocks);
        settings.add(blocksPerMinute);
        settings.add(reach);
        settings.add(toolSwap);
        settings.add(dropTrash);
        blocks.onChanged(v -> parseTargets());
    }

    @Override
    protected void init() {
        listenTick();
        parseTargets();
    }

    private void parseTargets() {
        targets.clear();
        for (String part : blocks.get().split(",")) {
            String t = part.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) continue;
            if (t.startsWith("minecraft:")) t = t.substring("minecraft:".length());
            targets.add(t);
        }
    }

    @Override
    protected void onEnable() {
        credit = 0;
        current = null;
        pauseTicks = 0;
    }

    @Override
    protected void onDisable() {
        current = null;
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (client.world == null || client.interactionManager == null || player == null) return;
        if (pauseTicks > 0) {
            pauseTicks--;
            return;
        }

        credit += blocksPerMinute.get() / (60.0 * 20.0); // bpm -> per tick
        if (credit < 1.0 && current == null) return;

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
        BlockPos next = findTarget(client, player);
        if (next == null) return;
        credit -= 1.0;
        if (toolSwap.get()) swapBestTool(client, player, client.world.getBlockState(next));
        current = next;
        mine(client, player, current);
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
        String id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString();
        if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
        return targets.contains(id);
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
            onBlockBroken(player);
        } else {
            boolean continuing = client.interactionManager.updateBlockBreakingProgress(pos, bhr.getSide());
            player.swingHand(Hand.MAIN_HAND);
            if (!continuing || client.world.getBlockState(pos).isAir()) {
                onBlockBroken(player);
            }
        }
    }

    private void onBlockBroken(ClientPlayerEntity player) {
        current = null;
        pauseTicks = 4; // small natural pause between blocks
        if (dropTrash.get() && player.getInventory().getEmptySlot() == -1) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (HotbarManager.isTrash(stack)) {
                    player.getInventory().selectedSlot = i;
                    player.dropSelectedItem(true);
                    break;
                }
            }
        }
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
}
