package com.soulrend;

import java.util.UUID;
import com.google.gson.Gson;
import okio.Buffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActivityEvidenceTransportTest {
    private PendingSubmissionStore.PendingSubmission pending() {
        var p = new PendingSubmissionStore.PendingSubmission();
        p.runeliteCaptureId = UUID.randomUUID().toString();
        p.eventId = 1;
        p.tileId = 2;
        p.timestamp = 1700000000000L;
        p.evidenceKind = "activity";
        p.evidenceId = 7;
        p.amount = 1;
        p.evidenceSource = "counter:42";
        p.activityEpoch = "a".repeat(64);
        p.activityEvidence = ActivityEvidenceContract.prepare(UUID.fromString(p.runeliteCaptureId),
            new ActivityObjectiveSignals.Signal("hunter_rumour", 42));
        return p;
    }

    @Test
    public void storedEnvelopeSurvivesRetryAndUsesFixedMultipartShape() throws Exception {
        var original = pending();
        var gson = new Gson();
        var restored = gson.fromJson(gson.toJson(original), PendingSubmissionStore.PendingSubmission.class);
        assertFalse(ActivityEvidenceContract.UPLOAD_READY);
        assertTrue(ActivityEvidenceContract.validPending(restored));
        var body = BingoApiClient.evidenceBody(restored, new byte[] {1, 2, 3});
        var buffer = new Buffer();
        body.writeTo(buffer);
        var wire = buffer.readUtf8();
        assertTrue(wire.contains("name=\"activity_evidence\""));
        assertTrue(wire.contains(restored.activityEvidence.toString()));
        assertEquals(original.activityEpoch, restored.activityEpoch);
        assertTrue(wire.contains("name=\"activity_epoch\""));
        assertTrue(wire.contains(original.activityEpoch));
        assertTrue(wire.contains("name=\"capture_id\""));
        assertTrue(wire.contains("name=\"captured_at\""));
        assertTrue(wire.contains("name=\"tile\""));
        assertTrue(wire.contains("name=\"image\""));
        assertFalse(wire.contains("name=\"quantity\""));
        assertFalse(wire.contains("name=\"source\""));
        assertFalse(wire.contains("name=\"kind\""));
    }

    @Test
    public void rejectsCorruptedOrMixedQueueEntries() {
        var proof = pending();
        proof.amount = 42;
        assertFalse(ActivityEvidenceContract.validPending(proof));
        proof = pending();
        proof.activityEvidence.addProperty("counter", "42");
        assertFalse(ActivityEvidenceContract.validPending(proof));
        proof = pending();
        proof.activityEvidence.addProperty("counter", 42.0);
        assertFalse(ActivityEvidenceContract.validPending(proof));
        proof = pending();
        proof.activityEvidence.addProperty("capture_id", UUID.randomUUID().toString());
        assertFalse(ActivityEvidenceContract.validPending(proof));
        proof = pending();
        proof.itemBatch = new com.google.gson.JsonObject();
        assertFalse(ActivityEvidenceContract.validPending(proof));
        proof = pending();
        proof.combatFact = "{}";
        assertFalse(ActivityEvidenceContract.validPending(proof));
        proof = pending();
        proof.activityEpoch = null;
        assertFalse(ActivityEvidenceContract.validPending(proof));
        proof.activityEpoch = "A".repeat(64);
        assertFalse(ActivityEvidenceContract.validPending(proof));
        proof.activityEpoch = "a".repeat(63);
        assertFalse(ActivityEvidenceContract.validPending(proof));
    }
}
