package com.soulrend;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActivityRuleEpochsTest {
    private ActivityObjectiveSignals.Signal signal(int n) {
        return new ActivityObjectiveSignals.Signal("hunter_rumour", n);
    }

    @Test
    public void separateBaselinesForNewTilesAndChangedUnlockEpoch() {
        var counters = new ActivityRuleEpochs();
        var a = new ActivityRuleEpochs.Entry(1, 1, "a".repeat(64));
        var b = new ActivityRuleEpochs.Entry(1, 2, "b".repeat(64));
        assertEquals(Set.of(), counters.observe("s", "rsn", signal(40), List.of(a)));
        assertEquals(Set.of(a), counters.observe("s", "rsn", signal(41), List.of(a,b)));
        assertEquals(Set.of(a,b), counters.observe("s", "rsn", signal(42), List.of(a,b)));
        var changed = new ActivityRuleEpochs.Entry(1, 1, "c".repeat(64));
        assertEquals(Set.of(b), counters.observe("s", "rsn", signal(43), List.of(changed,b)));
        assertEquals(Set.of(), counters.observe("s", "rsn", signal(43), List.of(changed,b)));
        assertEquals(Set.of(), counters.observe("s2", "rsn", signal(44), List.of(changed,b)));
        assertEquals(Set.of(changed,b), counters.observe("s2", "rsn", signal(45), List.of(changed,b)));
        counters.reset();
        assertEquals(Set.of(), counters.observe("s2", "rsn", signal(46), List.of(changed,b)));
    }

    @Test
    public void disappearanceWithoutSignalResetsOnlyRemovedRule() {
        var counters = new ActivityRuleEpochs();
        var a = new ActivityRuleEpochs.Entry(1, 1, "a".repeat(64));
        var b = new ActivityRuleEpochs.Entry(1, 2, "b".repeat(64));
        assertEquals(Set.of(), counters.observe("s", "rsn", signal(40), List.of(a,b)));
        counters.retain("s", "rsn", Set.of(b));
        counters.retain("s", "rsn", Set.of(a,b));
        assertEquals(Set.of(b), counters.observe("s", "rsn", signal(41), List.of(a,b)));
        assertEquals(Set.of(a,b), counters.observe("s", "rsn", signal(42), List.of(a,b)));
        counters.retain("different-session", "rsn", Set.of(a,b));
        assertEquals(Set.of(), counters.observe("different-session", "rsn", signal(43), List.of(a,b)));
    }

    @Test
    public void noUnknownOrUnversionedRules() {
        var rule = new ObjectiveRule();
        rule.kind = "activity";
        rule.activity = "hunter_rumour";
        rule.quantity = 5;
        assertFalse(rule.matchesActivity(signal(42)));
        rule.activity_epoch = "a".repeat(64);
        assertTrue(rule.matchesActivity(signal(42)));
        rule.activity = "lms";
        assertFalse(rule.matchesActivity(signal(42)));
        assertFalse(ActivityEvidenceContract.UPLOAD_READY);
    }
}
