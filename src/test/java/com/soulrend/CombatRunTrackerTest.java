package com.soulrend;

import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class CombatRunTrackerTest {
    private CombatCompletionParser.Completion completion() {
        return CombatCompletionParser.parse(List.of(
            "Tombs of Amascut: Expert Mode total completion time: 34:50",
            "Your completed Tombs of Amascut: Expert Mode count is: 8."));
    }

    @Test public void verifiedLifetimeCountsOnlyOwnDeaths() {
        CombatRunTracker tracker = new CombatRunTracker();
        tracker.start("session", "toa", "expert");
        tracker.localDeath("session");
        UUID capture = UUID.randomUUID();
        var fact = tracker.finish("session", capture, completion(), 290);
        assertEquals(capture, fact.captureId);
        assertEquals(Integer.valueOf(1), fact.ownDeaths);
        assertEquals(Integer.valueOf(290), fact.raidLevel);
        assertNull(tracker.finish("session", UUID.randomUUID(), completion(), null).ownDeaths);
    }

    @Test public void missingOrInterruptedStartIsUnknownNotZero() {
        CombatRunTracker tracker = new CombatRunTracker();
        assertNull(tracker.finish("session", UUID.randomUUID(), completion(), null).ownDeaths);
        tracker.start("session", "toa", "expert");
        tracker.invalidate();
        assertNull(tracker.finish("session", UUID.randomUUID(), completion(), null).ownDeaths);
        tracker.start("session", "toa", "expert");
        assertNull(tracker.finish("different", UUID.randomUUID(), completion(), null).ownDeaths);
        tracker.start("session", "toa", "normal");
        assertNull(tracker.finish("session", UUID.randomUUID(), completion(), null).ownDeaths);
        tracker.start("session", "toa", "expert");
        assertEquals(Integer.valueOf(0), tracker.finish("session", UUID.randomUUID(), completion(), null).ownDeaths);
    }

    @Test public void invalidLevelAndCaptureFailClosed() {
        CombatRunTracker tracker = new CombatRunTracker();
        assertNull(tracker.finish("session", UUID.randomUUID(), completion(), 1001).raidLevel);
        assertNull(tracker.finish("session", new UUID(0, 0), completion(), 300));
        assertNull(tracker.finish("session", UUID.randomUUID(), null, 300));
    }
}
