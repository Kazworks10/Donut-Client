package com.donut.module.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;

/**
 * Detects the server's combat tag: any visible scoreboard text (objective
 * names, display names, entry owners, team names) mentioning "combat".
 * Extracted verbatim from AutoLog; {@link #containsCombatKeyword} decision
 * logic lives in {@link AutoLogDecider} and is tested headless.
 */
public final class CombatTagScanner {
    private CombatTagScanner() {
    }

    /** True when any visible scoreboard text mentions "combat" (server combat tag). */
    public static boolean isCombatTagged(MinecraftClient client) {
        return scanScoreboard(client.world.getScoreboard());
    }

    /** Package-private core so the scoreboard walk can be tested headless. */
    static boolean scanScoreboard(Scoreboard sb) {
        for (ScoreboardObjective obj : sb.getObjectives()) {
            if (AutoLogDecider.containsCombatKeyword(obj.getName())
                    || AutoLogDecider.containsCombatKeyword(obj.getDisplayName().getString())) return true;
        }
        for (ScoreboardDisplaySlot slot : ScoreboardDisplaySlot.values()) {
            ScoreboardObjective obj = sb.getObjectiveForSlot(slot);
            if (obj == null) continue;
            if (AutoLogDecider.containsCombatKeyword(obj.getName())
                    || AutoLogDecider.containsCombatKeyword(obj.getDisplayName().getString())) return true;
            for (ScoreboardEntry entry : sb.getScoreboardEntries(obj)) {
                if (AutoLogDecider.containsCombatKeyword(entry.owner())) return true;
                Team team = sb.getScoreHolderTeam(entry.owner());
                if (team != null && (AutoLogDecider.containsCombatKeyword(team.getName())
                        || AutoLogDecider.containsCombatKeyword(team.getDisplayName().getString()))) return true;
            }
        }
        return false;
    }
}
