package com.donut.module.modules;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Combat-tag scan against a real headless {@link Scoreboard}: covers objective
 * names, display names, entry owners and team names — the exact surface the
 * scanner walks.
 */
class CombatTagScannerTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize(); // text/scoreboard registries + data fixers must exist headless
    }

    private final Scoreboard sb = new Scoreboard();

    private ScoreboardObjective objective(String name, String display) {
        return sb.addObjective(name, ScoreboardCriterion.DUMMY, Text.literal(display),
                ScoreboardCriterion.RenderType.INTEGER, false, BlankNumberFormat.INSTANCE);
    }

    /** The scanner core, called directly (package-private, same package). */
    private boolean scan() {
        return CombatTagScanner.scanScoreboard(sb);
    }

    @Test
    void emptyScoreboardIsNotTagged() throws Exception {
        assertFalse(scan());
    }

    @Test
    void combatInObjectiveNameIsTagged() throws Exception {
        objective("combat_tag", "Stats");
        assertTrue(scan());
    }

    @Test
    void combatInDisplayNameIsTagged() throws Exception {
        objective("stats", "Combat Log");
        assertTrue(scan());
    }

    @Test
    void combatInEntryOwnerIsTagged() {
        ScoreboardObjective obj = objective("kills", "Kills");
        sb.getOrCreateScore(ScoreHolder.fromName("CombatTimer"), obj).setScore(1);
        sb.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, obj); // entries scan visible boards
        assertTrue(scan());
    }

    @Test
    void combatInTeamNameIsTagged() {
        ScoreboardObjective obj = objective("kills", "Kills");
        sb.getOrCreateScore(ScoreHolder.fromName("Steve"), obj).setScore(1);
        sb.addTeam("combat_tagged");
        sb.addScoreHolderToTeam("Steve", sb.getTeam("combat_tagged"));
        sb.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, obj);
        assertTrue(scan());
    }

    @Test
    void unslottedEntriesAreNotScanned() {
        // entries on a board nobody displays are not visible -> not tagged
        ScoreboardObjective obj = objective("kills", "Kills");
        sb.getOrCreateScore(ScoreHolder.fromName("CombatTimer"), obj).setScore(1);
        assertFalse(scan());
    }

    @Test
    void ordinaryScoreboardIsNotTagged() {
        ScoreboardObjective obj = objective("money", "Balance");
        sb.getOrCreateScore(ScoreHolder.fromName("Steve"), obj).setScore(64);
        sb.addTeam("staff");
        sb.addScoreHolderToTeam("Steve", sb.getTeam("staff"));
        sb.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, obj);
        assertFalse(scan());
    }

    @Test
    void keywordIsCaseInsensitiveButSpecific() {
        assertTrue(AutoLogDecider.containsCombatKeyword("COMBAT-TAG"));
        assertTrue(AutoLogDecider.containsCombatKeyword("out of combat in 5s"));
        assertFalse(AutoLogDecider.containsCombatKeyword("combustion engine"));
    }
}
