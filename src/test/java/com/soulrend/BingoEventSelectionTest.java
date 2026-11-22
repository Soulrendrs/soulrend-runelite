package com.soulrend;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

/** Read-only bingo selection follows eligibility and never retains an old seat. */
public class BingoEventSelectionTest {
    private static BingoApiClient.EventSummary event(int id, String status) {
        BingoApiClient.EventSummary event = new BingoApiClient.EventSummary();
        event.id = id;
        event.name = "Event " + id;
        event.status = status;
        return event;
    }

    @Test public void automaticallyOpensActiveEvent() {
        var events = Arrays.asList(event(1, "upcoming"), event(2, "active"));
        assertEquals(2, SoulrendSidebarPanel.selectBingoEvent(events, 0).id);
    }

    @Test public void explicitSelectionSurvivesPolling() {
        var events = Arrays.asList(event(1, "upcoming"), event(2, "active"));
        assertEquals(1, SoulrendSidebarPanel.selectBingoEvent(events, 1).id);
    }

    @Test public void staleSelectionFallsBackWhenRosterChanges() {
        var events = Arrays.asList(event(3, "active"), event(4, "upcoming"));
        assertEquals(3, SoulrendSidebarPanel.selectBingoEvent(events, 2).id);
    }

    @Test public void emptyEligibilityReleasesOldEvent() {
        assertNull(SoulrendSidebarPanel.selectBingoEvent(Collections.emptyList(), 2));
        assertNull(SoulrendSidebarPanel.selectBingoEvent(null, 2));
    }
}
