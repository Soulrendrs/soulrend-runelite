package com.soulrend;

import org.junit.Test;

import java.util.Map;

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
