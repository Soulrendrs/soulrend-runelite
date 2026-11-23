package com.soulrend;

import org.junit.Test;

import static org.junit.Assert.*;

public class BingoCombatAchievementReminderTest {
    @Test
    public void reminderUsesOnlyVisibleTypedObjectives() {
        var tile = new BingoApiClient.BoardTile();
        tile.name = "Visible task";
        tile.status = "open";
        tile.objective_type = "ca";
        assertTrue(SoulrendSidebarPanel.visibleCombatAchievement(tile));
        tile.status = "locked";
        assertFalse(SoulrendSidebarPanel.visibleCombatAchievement(tile));
        tile.status = "hidden";
        assertFalse(SoulrendSidebarPanel.visibleCombatAchievement(tile));
        tile.status = "open";
        tile.objective_type = "manual";
        assertFalse(SoulrendSidebarPanel.visibleCombatAchievement(tile));
    }

    @Test
    public void instructionsIdentifyTheGameSettingAndWebsiteApproval() {
        String instructions = SoulrendSidebarPanel.CA_REPEAT_GUIDANCE;
        assertTrue(instructions.contains("Repeat completion"));
        assertTrue(instructions.contains("In OSRS"));
        assertTrue(instructions.contains("website handles approval"));
    }
}
