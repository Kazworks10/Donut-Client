package com.donut.module.modules;

import com.donut.module.Category;
import com.donut.module.Module;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Swaps to the best hotbar tool for the block you attack, only while the
 * attack key is held — identical trigger to the reference implementation this
 * was ported from.
 */
public final class AutoTool extends Module {
    public AutoTool() {
        super("AutoTool", "Hotbar-swaps to the best tool for what you attack", Category.MISC);
    }

    @Override
    protected void init() {
        listenTick();
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null) return;
        if (!client.options.attackKey.isPressed()) return;

        if (client.crosshairTarget instanceof BlockHitResult hit
                && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            switchToBestTool(client, hit.getBlockPos());
        }
    }

    private void switchToBestTool(MinecraftClient client, BlockPos pos) {
        BlockState state = client.world.getBlockState(pos);
        if (state.isAir() || state.isOf(Blocks.BEDROCK)) return;

        PlayerInventory inv = client.player.getInventory();
        List<Double> speeds = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            speeds.add(miningSpeed(inv.getStack(slot), state));
        }
        int target = AutoToolSelector.select(new AutoToolSelector.Scores(speeds), inv.selectedSlot);
        if (target >= 0) inv.selectedSlot = target;
    }

    /** Mining speed multiplier, or -1 when the stack is not a suitable tool. */
    static double miningSpeed(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) return -1;
        boolean swordOnCobweb = state.isOf(Blocks.COBWEB)
                && Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_sword");
        if (!stack.isSuitableFor(state) && !swordOnCobweb) return -1;
        return stack.getMiningSpeedMultiplier(state);
    }
}
