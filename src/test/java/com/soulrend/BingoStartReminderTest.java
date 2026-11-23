package com.soulrend;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BingoStartReminderTest {
    private static BingoApiClient.EventSummary event(long start, String status) {
        var event = new BingoApiClient.EventSummary();
        event.id = 1;
        event.start_at_epoch = start;
        event.status = status;
        return event;
    }

    @Test
    public void remindsOnceWithinTenMinutesOfScheduledStart() {
        var reminder = new BingoStartReminder();
        var upcoming = event(1000, "upcoming");
        assertFalse(reminder.claim(upcoming, 399));
        assertTrue(reminder.claim(upcoming, 400));
        assertFalse(reminder.claim(upcoming, 500));
        assertFalse(reminder.claim(upcoming, 1000));
        assertFalse(reminder.claim(upcoming, 1001));
    }

    @Test
    public void skipsActiveAndUnknownStartsAndAllowsRescheduledReminder() {
        var reminder = new BingoStartReminder();
        assertFalse(reminder.claim(event(1000, "active"), 800));
        assertFalse(reminder.claim(event(0, "upcoming"), 800));
        assertTrue(reminder.claim(event(1000, "upcoming"), 800));
        assertFalse(reminder.claim(event(1500, "upcoming"), 899));
        assertTrue(reminder.claim(event(1500, "upcoming"), 900));
    }
}
