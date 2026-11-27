package com.soulrend;

import com.google.gson.JsonObject;
import java.util.UUID;

/** Inactive activity payload builder. No transport, scoring or client API access. */
final class ActivityEvidenceContract {
    // Not configurable or advertised until detector and server scoring signoff.
    static final boolean UPLOAD_READY = false;

    /** Validate restored durable metadata before it could be used for transport. */
    static boolean validPending(PendingSubmissionStore.PendingSubmission proof) {
        if (proof == null || !"activity".equals(proof.evidenceKind) || proof.itemBatch != null
            || proof.activityEvidence == null || !validEpoch(proof.activityEpoch) || proof.amount != 1 || proof.evidenceRepeat
            || proof.runeliteCaptureId == null || proof.eventId <= 0 || proof.tileId <= 0) return false;
        try {
            var data = proof.activityEvidence;
            if (data.entrySet().size() != 4 || !data.has("version") || !data.has("capture_id")
                || !data.has("activity_id") || !data.has("counter")
                || !data.get("capture_id").isJsonPrimitive()
                || !data.getAsJsonPrimitive("capture_id").isString()
                || !proof.runeliteCaptureId.equals(data.get("capture_id").getAsString())) return false;
            UUID id = UUID.fromString(proof.runeliteCaptureId);
            if (id.version() != 4 || id.variant() != 2 || !id.toString().equals(proof.runeliteCaptureId)
                || number(data, "version") != 1) return false;
            int identity = number(data, "activity_id"), counter = number(data, "counter");
            return identity == proof.evidenceId && ActivityObjectiveSignals.IDS.containsValue(identity)
                && counter > 0 && ("counter:" + counter).equals(proof.evidenceSource);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    static boolean validEpoch(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static int number(JsonObject data, String key) {
        var value = data.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
            || !value.getAsString().matches("[1-9][0-9]{0,9}")) return 0;
        return Integer.parseInt(value.getAsString());
    }

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
