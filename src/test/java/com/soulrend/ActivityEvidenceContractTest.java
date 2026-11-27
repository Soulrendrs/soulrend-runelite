package com.soulrend;

import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActivityEvidenceContractTest {
    @Test
    public void stableOccurrenceEnvelope() {
        var id = UUID.fromString("d5aa1a3c-6dda-4d04-b9f4-40f2c7064a18");
        var signal = ActivityObjectiveSignals.parse("You have completed 42 rumours for the Hunter Guild.");
        var data = ActivityEvidenceContract.prepare(id, signal);
        assertNotNull(data);
        assertEquals(4, data.entrySet().size());
        assertEquals(1, data.get("version").getAsInt());
        assertEquals(id.toString(), data.get("capture_id").getAsString());
        assertEquals(7, data.get("activity_id").getAsInt());
        assertEquals(42, data.get("counter").getAsInt());
        assertEquals(data, ActivityEvidenceContract.prepare(id, signal));
        assertFalse(data.has("quantity"));
        assertFalse(data.has("message"));
    }

    @Test
    public void rejectsUnknownAndInvalidSignals() {
        var id = UUID.randomUUID();
        assertNull(ActivityEvidenceContract.prepare(id, null));
        assertNull(ActivityEvidenceContract.prepare(id, new ActivityObjectiveSignals.Signal("lms", 1)));
        assertNull(ActivityEvidenceContract.prepare(id, new ActivityObjectiveSignals.Signal("hunter_rumour", 0)));
        assertNull(ActivityEvidenceContract.prepare(null, new ActivityObjectiveSignals.Signal("hunter_rumour", 1)));
        assertNull(ActivityEvidenceContract.prepare(new UUID(0, 0),
            new ActivityObjectiveSignals.Signal("hunter_rumour", 1)));
    }
}
