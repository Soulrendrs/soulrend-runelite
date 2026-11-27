package com.soulrend;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActivityObjectiveSignalsTest {
    @Test
    public void sharedVectors() throws Exception {
        try (var stream = getClass().getResourceAsStream("/runelite_activity_contract_v1.json")) {
            assertNotNull(stream);
            var fixture = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var ids = fixture.getAsJsonObject("activities");
            assertEquals(ids.size(), ActivityObjectiveSignals.IDS.size());
            for (var row : ids.entrySet()) {
                assertEquals(row.getValue().getAsInt(), ActivityObjectiveSignals.IDS.get(row.getKey()).intValue());
            }
            for (var element : fixture.getAsJsonArray("signals")) {
                var row = element.getAsJsonObject();
                var signal = ActivityObjectiveSignals.parse(row.get("message").getAsString());
                if (row.get("activity").isJsonNull()) assertNull(signal);
                else {
                    assertNotNull(signal);
                    assertEquals(row.get("activity").getAsString(), signal.activity);
                    assertEquals(row.get("count").getAsInt(), signal.count);
                }
            }
        }
    }

    @Test
    public void onlyConsecutiveIncreasesWithinEpoch() {
        var counter = new ActivityObjectiveSignals.ConsecutiveCounter();
        assertFalse(counter.observe(rumour(40)));
        assertTrue(counter.observe(rumour(41)));
        assertFalse(counter.observe(rumour(41)));
        assertFalse(counter.observe(rumour(39)));
        assertFalse(counter.observe(rumour(44)));
        assertTrue(counter.observe(rumour(45)));
        assertFalse(counter.observe(new ActivityObjectiveSignals.Signal("sepulchre_floor_1", 45)));
        assertFalse(counter.observe(new ActivityObjectiveSignals.Signal("lms", 1)));
        counter.reset();
        assertFalse(counter.observe(rumour(46)));
        assertTrue(counter.observe(rumour(47)));
    }

    private static ActivityObjectiveSignals.Signal rumour(int n) {
        return new ActivityObjectiveSignals.Signal("hunter_rumour", n);
    }
}
