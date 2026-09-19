package com.donut.mining;

import com.donut.pathfinding.MovementInputOverride;
import com.donut.rotation.RotationUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Single owner of input-level steering: AutoMine and SchematicBuilder use the
 * world-space variant for straight-line walking, PathExecutor feeds its
 * waypoint motion through the player-local variant. Pure input injection —
 * vanilla physics handle the rest.
 */
public final class MovementSteering {
    private MovementSteering() {
    }

    /**
     * World-space steering: ease the real camera yaw toward the target and
     * press forward, jumping when a one-block step sits directly ahead. The
     * caller must have activated {@link MovementInputOverride}.
     */
    public static void steerToward(MinecraftClient client, ClientPlayerEntity player, Vec3d target) {
        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz <= 1.0) return;

        float wantYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float dyaw = RotationUtils.delta(player.getYaw(), wantYaw);
        player.setYaw(player.getYaw() + RotationUtils.wrapDegrees(dyaw * 0.3f));
        boolean jump = client.world.getBlockState(player.getBlockPos().up()).isReplaceable()
                && client.world.getBlockState(player.getBlockPos().up(2)).isReplaceable()
                && client.world.getBlockState(player.getBlockPos().down())
                        .isSolidBlock(client.world, player.getBlockPos());
        MovementInputOverride.set(1f, 0f, jump, false, false);
    }

    /**
     * Player-local motion injection for waypoint followers: forward/strafe in
     * player axes plus jump/sneak/sprint flags.
     */
    public static void applyLocal(float forward, float strafe, boolean jump, boolean sneak, boolean sprint) {
        MovementInputOverride.set(forward, strafe, jump, sneak, sprint);
    }
}
