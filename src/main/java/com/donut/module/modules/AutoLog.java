package com.donut.module.modules;

import com.donut.module.Category;
import com.donut.module.Module;
import com.donut.module.settings.BooleanSetting;
import com.donut.module.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.Team;

/**
 * Logs out of the server when a panic condition is met, then disables itself:
 * configurable health threshold, nearby-player detection, and the server's
 * "combat" scoreboard tag (Donut SMP combat-logging protection). Native
 * rewrite of the reference AutoLog: the scoreboard check replaces the
 * original's reflection-based HUD traversal.
 */
public final class AutoLog extends Module {
    private final NumberSetting healthThreshold = new NumberSetting("Health", 8.0, 1.0, 20.0, 0.5, 1,
            "Log out when health + absorption drops below this");
    private final BooleanSetting playersNear = new BooleanSetting("Logout On Player Nearby", false,
            "Log out when another player comes within range");
    private final NumberSetting playerRange = new NumberSetting("Player Range", 12.0, 4.0, 32.0, 1.0, 0,
            "Nearby-player detection radius");
    private final BooleanSetting combatTag = new BooleanSetting("Respect Combat Tag", true,
            "Never log out while a combat scoreboard is visible");
    private final NumberSetting logoutDelay = new NumberSetting("Logout Delay (s)", 0.0, 0.0, 10.0, 0.5, 1,
            "Seconds between trigger and disconnect");

    private double lastHealth = -1.0;
    private boolean triggered;
    private int delayTicks;

    public AutoLog() {
        super("AutoLog", "Disconnects on low health, nearby players, or combat trouble", Category.MISC);
        settings.add(healthThreshold);
        settings.add(playersNear);
        settings.add(playerRange);
        settings.add(combatTag);
        settings.add(logoutDelay);
    }

    @Override
    protected void init() {
        listenTick();
    }

    @Override
    protected void onEnable() {
        MinecraftClient client = MinecraftClient.getInstance();
        lastHealth = client.player != null ? combinedHealth(client) : -1.0;
        triggered = false;
        delayTicks = 0;
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (triggered) {
            if (delayTicks > 0) delayTicks--;
            if (delayTicks == 0) disconnect(client, triggeredReason);
            return;
        }

        double health = combinedHealth(client);
        boolean dropped = lastHealth >= 0.0 && health + 0.001 < lastHealth;
        lastHealth = health;

        String reason = null;
        if (dropped && health < healthThreshold.get()) {
            reason = String.format("health %.1f < %.1f", health, healthThreshold.get());
        } else if (playersNear.get() && nearbyPlayer(client, playerRange.get())) {
            reason = "player nearby";
        } else if (dropped && combatTag.get() && isCombatTagged(client)) {
            reason = "took damage while combat-tagged";
        }

        if (reason != null) {
            triggeredReason = reason;
            triggered = true;
            delayTicks = (int) Math.round(logoutDelay.get() * 20);
            if (delayTicks == 0) disconnect(client, reason);
        }
    }

    private String triggeredReason;

    private void disconnect(MinecraftClient client, String reason) {
        triggered = false;
        if (client.getNetworkHandler() != null && client.getNetworkHandler().getConnection() != null) {
            client.getNetworkHandler().getConnection().disconnect(
                    net.minecraft.text.Text.literal("[AutoLog] " + reason));
        }
        disable();
    }

    private static double combinedHealth(MinecraftClient client) {
        return client.player.getHealth() + client.player.getAbsorptionAmount();
    }

    private static boolean nearbyPlayer(MinecraftClient client, double range) {
        double rangeSq = range * range;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p != client.player && !p.isSpectator()
                    && client.player.squaredDistanceTo(p) <= rangeSq) return true;
        }
        return false;
    }

    /** True when any visible scoreboard text mentions "combat" (server combat tag). */
    static boolean isCombatTagged(MinecraftClient client) {
        Scoreboard sb = client.world.getScoreboard();
        for (ScoreboardObjective obj : sb.getObjectives()) {
            if (containsCombat(obj.getName()) || containsCombat(obj.getDisplayName().getString())) return true;
        }
        for (ScoreboardDisplaySlot slot : ScoreboardDisplaySlot.values()) {
            ScoreboardObjective obj = sb.getObjectiveForSlot(slot);
            if (obj == null) continue;
            if (containsCombat(obj.getName()) || containsCombat(obj.getDisplayName().getString())) return true;
            for (ScoreboardEntry entry : sb.getScoreboardEntries(obj)) {
                if (containsCombat(entry.owner())) return true;
                Team team = sb.getScoreHolderTeam(entry.owner());
                if (team != null && (containsCombat(team.getName())
                        || containsCombat(team.getDisplayName().getString()))) return true;
            }
        }
        return false;
    }

    private static boolean containsCombat(String s) {
        return s != null && s.toLowerCase(java.util.Locale.ROOT).contains("combat");
    }
}
