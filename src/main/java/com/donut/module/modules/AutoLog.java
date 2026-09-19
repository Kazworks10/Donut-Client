package com.donut.module.modules;

import com.donut.module.Category;
import com.donut.module.Module;
import com.donut.module.settings.BooleanSetting;
import com.donut.module.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

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

        AutoLogDecider.Decision decision = AutoLogDecider.decide(health, dropped,
                healthThreshold.get(), combatTag.get(),
                () -> CombatTagScanner.isCombatTagged(client), logoutDelay.get());

        String reason;
        int delay;
        if (decision.trigger()) {
            reason = decision.reason();
            delay = decision.delayTicks();
        } else if (playersNear.get() && nearbyPlayer(client, playerRange.get())) {
            reason = "player nearby";
            delay = AutoLogDecider.delayTicks(logoutDelay.get());
        } else {
            reason = null;
            delay = 0;
        }

        if (reason != null) {
            triggeredReason = reason;
            triggered = true;
            delayTicks = delay;
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

}
