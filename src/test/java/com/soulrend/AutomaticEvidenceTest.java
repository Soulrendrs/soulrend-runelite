package com.soulrend;

import net.runelite.client.game.ItemStack;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutomaticEvidenceTest {
    @Test
    public void ordinaryStackMustMeetItemSourceAndQuantityRules() {
        var rule = new AutomaticEvidence.Rule();
        rule.kind = "drop";
        rule.item_id = 995;
        rule.quantity = 5;
        rule.source = "npc";
        assertTrue(rule.matchesDrop(Map.of(995, 5), "npc"));
        assertFalse(rule.matchesDrop(Map.of(995, 4), "npc"));
        assertFalse(rule.matchesDrop(Map.of(996, 10), "npc"));
        assertFalse(rule.matchesDrop(Map.of(995, 5), "pvp"));
    }

    @Test
    public void inventoryGainNeedsCurrentUnambiguousSkillXpAndMatchingStack() {
        var rule = new AutomaticEvidence.Rule();
        rule.kind = "gain";
        rule.item_id = 331;
        rule.source = "fishing";
        rule.quantity = 2;
        assertFalse(rule.matchesGain("mining", Map.of(331, 2)));
        assertFalse(rule.matchesGain("fishing", Map.of(331, 1)));
        assertTrue(rule.matchesGain("fishing", Map.of(331, 2)));
        org.junit.Assert.assertNull(AutomaticEvidence.gatheringSkill(Map.of(), 8));
        org.junit.Assert.assertNull(AutomaticEvidence.gatheringSkill(Map.of("fishing", 7), 8));
        org.junit.Assert.assertNull(AutomaticEvidence.gatheringSkill(Map.of("fishing", 8, "mining", 8), 8));
        org.junit.Assert.assertEquals("fishing", AutomaticEvidence.gatheringSkill(Map.of("fishing", 8), 8));
    }

    @Test
    public void notedStacksAlsoMatchTheirBaseItemWithoutLosingExactIds() {
        var quantities = AutomaticEvidence.lootQuantities(List.of(
            new ItemStack(537, 3), new ItemStack(536, 2), new ItemStack(537, 0)),
            id -> id == 537 ? 536 : id);
        assertEquals(Map.of(536, 5, 537, 3), quantities);
    }

    @Test
    public void firstCompletionsCanShareATickAndMixedBatchesStayAmbiguous() {
        var tasks = List.of(
            new SoulrendPlugin.PendingCaTask(CombatAchievementTier.EASY, "Example"),
            new SoulrendPlugin.PendingCaTask(CombatAchievementTier.HARD, "Another Task"),
            new SoulrendPlugin.PendingCaTask(CombatAchievementTier.HARD, "another task"));
        assertEquals(List.of("example", "another task"), AutomaticEvidence.confirmedCombatTasks(tasks, 4));
        assertEquals(List.of("example", "another task"), AutomaticEvidence.confirmedCombatTasks(tasks, 0));
        assertEquals(List.of(), AutomaticEvidence.confirmedCombatTasks(tasks, 3));
        assertEquals(List.of(), AutomaticEvidence.confirmedCombatTasks(tasks, -1));
    }

    @Test
    public void repeatCombatTasksRequireAnExplicitRule() {
        var rule = new AutomaticEvidence.Rule();
        rule.kind = "ca";
        rule.task_id = 123;
        rule.task_name = "A task";
        assertTrue(rule.matchesTask("a TASK", false));
        assertFalse(rule.matchesTask("A task", true));
        rule.allow_repeat = true;
        assertTrue(rule.matchesTask("A task", true));
        assertFalse(rule.matchesTask("Another task", true));
    }
}
