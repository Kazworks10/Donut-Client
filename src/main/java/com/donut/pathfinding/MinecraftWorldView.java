package com.donut.pathfinding;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Thin adapter from {@link World} to the pure {@link WorldView}. Reads are cheap
 * (state lookup + instanceof checks); searches run off the client thread and the
 * world instance is captured per search, so no stale-client references are held.
 */
public final class MinecraftWorldView implements WorldView {
    private final World world;

    public MinecraftWorldView(World world) {
        this.world = world;
    }

    @Override
    public BlockProps block(int x, int y, int z) {
        if (y < world.getBottomY() || y >= world.getTopY()) return null;
        BlockPos pos = new BlockPos(x, y, z);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return BlockProps.AIR;
        if (state.isOf(Blocks.WATER)) return BlockProps.WATER;
        if (state.isOf(Blocks.LAVA)) return BlockProps.LAVA;
        if (state.isOf(Blocks.LADDER) || state.isOf(Blocks.VINE)) return BlockProps.LADDER;
        if (state.isOf(Blocks.CACTUS) || state.isOf(Blocks.SWEET_BERRY_BUSH)) return BlockProps.CACTUS;
        if (state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.MAGMA_BLOCK)) return BlockProps.FIRE;
        if (state.isOf(Blocks.OAK_LEAVES) || state.isOf(Blocks.BIRCH_LEAVES) || state.isOf(Blocks.SPRUCE_LEAVES)
                || state.isOf(Blocks.JUNGLE_LEAVES) || state.isOf(Blocks.ACACIA_LEAVES) || state.isOf(Blocks.DARK_OAK_LEAVES)
                || state.isOf(Blocks.AZALEA_LEAVES) || state.isOf(Blocks.CHERRY_LEAVES) || state.isOf(Blocks.MANGROVE_LEAVES)) {
            return BlockProps.LEAVES;
        }
        boolean solid = state.isSolidBlock(world, pos);
        return solid ? BlockProps.SOLID : BlockProps.AIR;
    }

    @Override
    public boolean isLoaded(int x, int z) {
        return world.isChunkLoaded(x >> 4, z >> 4);
    }

    @Override
    public int minY() {
        return world.getBottomY();
    }

    @Override
    public int maxY() {
        return world.getTopY() - 1;
    }
}
