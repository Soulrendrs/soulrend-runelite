package com.soulrend;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/** Real kill evidence is one observation per NPC, regardless of which RuneLite event arrived first. */
public class NpcKillEvidenceSignalsTest {
    @Test public void serverThenClientCreditsOnce() {
        var tracker = new NpcKillEvidenceSignals();
        assertTrue(tracker.server(655, 100));
        tracker.client(655, 101);
        assertEquals(List.of(), tracker.expire(103));
    }

    @Test public void clientThenServerCreditsOnce() {
        var tracker = new NpcKillEvidenceSignals();
        tracker.client(655, 100);
        assertTrue(tracker.server(655, 101));
        assertEquals(List.of(), tracker.expire(103));
    }

    @Test public void clientOnlyStillCountsAfterUnrelatedServerEvent() {
        var tracker = new NpcKillEvidenceSignals();
        assertTrue(tracker.server(1118, 100)); // Some NPCs fire the server signal.
        tracker.client(655, 101);              // Other NPCs may have only client loot.
        assertEquals(List.of(), tracker.expire(102));
        assertEquals(List.of(655), tracker.expire(103));
        assertEquals(List.of(), tracker.expire(104));
    }

    @Test public void multipleSameTickServerKillsAreNotCollapsed() {
        var tracker = new NpcKillEvidenceSignals();
        assertTrue(tracker.server(655, 10));
        assertTrue(tracker.server(655, 10));
        tracker.client(655, 10); // Inferred client loot can undercount multi-kills.
        assertEquals(List.of(), tracker.expire(12));
    }

    @Test public void lateServerSignalDoesNotDoubleCreditFallback() {
        var tracker = new NpcKillEvidenceSignals();
        tracker.client(655, 10);
        assertEquals(List.of(655), tracker.expire(12));
        assertFalse(tracker.server(655, 13));
    }

    @Test public void differentNpcIdsAreNeverMistakenForTheSameKill() {
        var tracker = new NpcKillEvidenceSignals();
        assertTrue(tracker.server(655, 100));
        tracker.client(674, 100);
        assertEquals(List.of(674), tracker.expire(102));
    }

    @Test public void sessionResetDiscardsPendingClientEvents() {
        var tracker = new NpcKillEvidenceSignals();
        tracker.client(655, 100);
        tracker.clear();
        assertEquals(List.of(), tracker.expire(103));
    }
}
