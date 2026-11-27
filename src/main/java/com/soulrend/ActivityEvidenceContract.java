package com.soulrend;

import com.google.gson.JsonObject;
import java.util.UUID;

/** Inactive activity payload builder. No transport, scoring or client API access. */
final class ActivityEvidenceContract {
    static JsonObject prepare(UUID captureId, ActivityObjectiveSignals.Signal signal) {
        if (captureId == null || captureId.version() != 4 || captureId.variant() != 2
            || signal == null || signal.activity == null || signal.count <= 0) return null;
        Integer id = ActivityObjectiveSignals.IDS.get(signal.activity);
        if (id == null) return null;
        var payload = new JsonObject();
        payload.addProperty("version", 1);
        payload.addProperty("capture_id", captureId.toString());
        payload.addProperty("activity_id", id);
        payload.addProperty("counter", signal.count);
        return payload;
    }

    private ActivityEvidenceContract() {}
}
