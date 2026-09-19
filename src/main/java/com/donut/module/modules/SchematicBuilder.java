package com.donut.module.modules;

import com.donut.module.Category;
import com.donut.module.Module;
import com.donut.module.settings.BooleanSetting;
import com.donut.module.settings.EnumSetting;
import com.donut.module.settings.NumberSetting;
import com.donut.module.settings.StringSetting;
import com.donut.pathfinding.MovementInputOverride;
import com.donut.schematic.BuildSession;
import com.donut.schematic.GhostRenderer;
import com.donut.schematic.PlacementEngine;
import com.donut.schematic.PlacementPlanner;
import com.donut.schematic.SchematicData;
import com.donut.schematic.SchematicFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Builds a schematic file at the marked origin. Placement is rate-limited and
 * goes through the vanilla interaction path; walking between placements is
 * handled by simple steering toward the current target (no auto-scaffold).
 * Progress is persisted so a disconnect can resume the same build.
 */
public final class SchematicBuilder extends Module {
    public enum Mode { LAYER, SPIRAL, NEAREST }

    private final StringSetting file = new StringSetting("Schematic", "mybuild.litematic",
            "File name inside the schematics/ directory");
    private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.LAYER, "Placement ordering");
    private final NumberSetting blocksPerMinute = new NumberSetting("Speed (BPM)", 180, 10, 900, 5, 0,
            "Placement rate");
    private final BooleanSetting persist = new BooleanSetting("Resume On Disconnect", true,
            "Save progress and offer resume");
    private final BooleanSetting walkToTargets = new BooleanSetting("Walk To Targets", true,
            "Steer toward blocks that are too far to place");

    private final PlacementEngine engine = new PlacementEngine();
    private SchematicData schematic;
    private BlockPos origin;
    private Path schematicsDir;
    private BuildSession session;
    private boolean loading;
    private long lastSaveMs;
    private String status = "no schematic loaded";

    public SchematicBuilder() {
        super("SchematicBuilder", "Places schematic blocks at your feet (origin)", Category.WORLD);
        settings.add(file);
        settings.add(mode);
        settings.add(blocksPerMinute);
        settings.add(persist);
        settings.add(walkToTargets);
    }

    @Override
    protected void init() {
        listenTick();
    }

    public void setSchematicsDir(Path dir) {
        this.schematicsDir = dir;
    }

    public String status() {
        return status;
    }

    public BlockPos origin() {
        return origin;
    }

    /** Loads the configured schematic and starts a build at the player position. */
    public void startHere(MinecraftClient client) {
        if (client.player == null || schematicsDir == null || loading) return;
        loading = true;
        status = "loading " + file.get() + "...";
        CompletableFuture.supplyAsync(() -> {
            try {
                Path resolved = schematicsDir.resolve(file.get().trim());
                if (!Files.exists(resolved)) throw new java.io.IOException("file not found: " + resolved);
                SchematicData data = SchematicFormat.parse(resolved);
                BuildSession prior = persist.get()
                        ? BuildSession.load(schematicsDir, file.get().trim()) : null;
                return new Object[]{data, prior};
            } catch (Exception e) {
                return new Object[]{e};
            }
        }).whenComplete((r, t) -> {
            loading = false;
            if (r.length == 1) {
                status = "load failed: " + r[0];
                return;
            }
            schematic = (SchematicData) r[0];
            session = (BuildSession) r[1];
            origin = session != null
                    ? new BlockPos(session.originX, session.originY, session.originZ)
                    : BlockPos.ofFloored(client.player.getPos());
            List<int[]> plan = PlacementPlanner.plan(schematic, mode.get().name().toLowerCase(), 0, 0);
            engine.begin(plan, schematic.palette());
            if (session != null) {
                engine.setIndex(Math.min(session.index, plan.size()));
                status = "resumed at " + session.index + "/" + plan.size();
            } else {
                session = new BuildSession(file.get().trim(), origin.getX(), origin.getY(), origin.getZ(),
                        mode.get().name().toLowerCase(), 0);
                status = "started " + plan.size() + " blocks";
            }
        });
    }

    /** Stops building and persists the session. */
    public void stop() {
        engine.stop();
        GhostRenderer.clear();
        if (session != null && schematicsDir != null && persist.get()) {
            session.index = engine.index();
            session.save(schematicsDir);
        }
        status = "stopped at " + (session != null ? session.index : 0);
    }

    /** Sets origin to the player's current position (used before starting). */
    public void markOrigin(MinecraftClient client) {
        if (client.player != null) {
            origin = BlockPos.ofFloored(client.player.getPos());
            status = "origin marked at " + origin;
        }
    }

    @Override
    protected void onDisable() {
        stop();
        MovementInputOverride.end();
    }

    @Override
    protected void onTick() {
        // Publish build state for the ghost preview (no-op when not building)
        GhostRenderer.update(engine.plan(), engine.index(), origin);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || schematic == null || engine.isDone()) {
            if (schematic != null && engine.isDone()) {
                status = "done: " + engine.placedCount() + " placed";
                MovementInputOverride.end();
            }
            return;
        }

        int[] cell = currentCell();
        if (cell == null) return;
        BlockPos target = origin.add(cell[0], cell[1], cell[2]);

        // Walk toward the target if it is out of placement range
        double distSq = client.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target));
        double maxRange = 4.5 * 4.5;
        if (walkToTargets.get() && distSq > maxRange) {
            steerToward(client, target);
        } else {
            MovementInputOverride.end();
            boolean placed = engine.tick(client, blocksPerMinute.floatValue(), origin);
            if (placed && persist.get() && schematicsDir != null
                    && System.currentTimeMillis() - lastSaveMs > 5000) {
                session.index = engine.index();
                session.save(schematicsDir);
                lastSaveMs = System.currentTimeMillis();
            }
        }
    }

    private int[] currentCell() {
        if (engine.plan() == null || engine.index() >= engine.plan().size()) return null;
        return engine.plan().get(engine.index());
    }

    /** Very simple steering: face the target and press forward; jump for steps. */
    private void steerToward(MinecraftClient client, BlockPos target) {
        var player = client.player;
        Vec3d center = Vec3d.ofCenter(target);
        double dx = center.x - player.getX();
        double dz = center.z - player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz > 1.0) {
            float wantYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            float dyaw = com.donut.rotation.RotationUtils.delta(player.getYaw(), wantYaw);
            player.setYaw(player.getYaw() + com.donut.rotation.RotationUtils.wrapDegrees(dyaw * 0.3f));
            boolean jump = client.world.getBlockState(client.player.getBlockPos().up()).isReplaceable()
                    && client.world.getBlockState(client.player.getBlockPos().up(2)).isReplaceable()
                    && client.world.getBlockState(client.player.getBlockPos().down()).isSolidBlock(client.world, client.player.getBlockPos());
            MovementInputOverride.begin();
            MovementInputOverride.set(1f, 0f, jump, false, false);
        }
    }

    /** Clears the stored session (called from the GUI "reset" action). */
    public void clearSession() {
        session = null;
        schematic = null;
        engine.stop();
        GhostRenderer.clear();
        status = "session cleared";
    }
}
