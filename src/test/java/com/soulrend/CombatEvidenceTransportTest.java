package com.soulrend;

import com.google.gson.Gson;
import java.util.List;
import java.util.UUID;
import okio.Buffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class CombatEvidenceTransportTest {
    private PendingSubmissionStore.PendingSubmission pending() {
        var proof = new PendingSubmissionStore.PendingSubmission();
        var id = UUID.randomUUID();
        proof.runeliteCaptureId = id.toString();
        proof.eventId = 1;
        proof.tileId = 2;
        proof.timestamp = 1700000000000L;
        proof.evidenceKind = "combat_clear";
        proof.evidenceSource = "combat";
        proof.amount = 1;
        proof.combatFact = CombatEvidenceContract.encode(id, CombatCompletionParser.parse(List.of(
            "Your Vorkath kill count is: 1,234.", "Fight duration: 1:36.01")));
        return proof;
    }

    @Test public void queueRoundTripKeepsFixedEnvelope() throws Exception {
        var proof = pending();
        var gson = new Gson();
        var restored = gson.fromJson(gson.toJson(proof), PendingSubmissionStore.PendingSubmission.class);
        assertEquals(proof.combatFact, restored.combatFact);
        assertTrue(CombatEvidenceContract.validPending(restored));
        var buffer = new Buffer();
        BingoApiClient.evidenceBody(restored, new byte[]{1, 2, 3}).writeTo(buffer);
        String wire = buffer.readUtf8();
        for (String name : List.of("capture_id", "captured_at", "tile", "combat_fact", "image"))
            assertTrue(wire.contains("name=\"" + name + "\""));
        for (String name : List.of("quantity", "kind", "objective_id", "activity_evidence", "item_batch", "source"))
            assertFalse(wire.contains("name=\"" + name + "\""));
        assertTrue(wire.contains(restored.combatFact));
    }

    @Test public void corruptedQueueDoesNotSend() {
        var proof = pending();
        proof.amount = 2;
        assertFalse(CombatEvidenceContract.validPending(proof));
        proof = pending();
        proof.runeliteCaptureId = UUID.randomUUID().toString();
        assertFalse(CombatEvidenceContract.validPending(proof));
        proof = pending();
        proof.combatFact = proof.combatFact.replace("\"counter\":1234", "\"counter\":1234.0");
        assertFalse(CombatEvidenceContract.validPending(proof));
        proof = pending();
        proof.itemBatch = new com.google.gson.JsonObject();
        assertFalse(CombatEvidenceContract.validPending(proof));
        proof = pending();
        proof.activityEpoch = "a".repeat(64);
        assertFalse(CombatEvidenceContract.validPending(proof));
        proof = pending();
        proof.tileId = 0;
        assertFalse(CombatEvidenceContract.validPending(proof));
        proof = pending();
        String originalId = proof.runeliteCaptureId;
        String invalidVariant = "12345678-1234-4abc-0123-123456789abc";
        proof.runeliteCaptureId = invalidVariant;
        proof.combatFact = proof.combatFact.replace(originalId, invalidVariant);
        assertFalse(CombatEvidenceContract.validPending(proof));
    }

    @Test public void sameTickAndExplicitTrustedChannelOnly() {
        var window = new CombatCompletionWindow();
        window.offer("Your Vorkath kill count is: 42.", false, 10);
        window.offer("Fight duration: 1:36.01", false, 10);
        var clear = window.finish(11);
        assertNotNull(clear);
        assertEquals(42, clear.counter);
        assertNull(window.finish(11));
        window.offer("Your Vorkath kill count is: 43.", false, 11);
        window.offer("Fight duration: 1:36.01", false, 12);
        assertNull(window.finish(12));
        window.offer("Your Vorkath kill count is: 44.", true, 13);
        window.offer("Fight duration: 1:36.01", false, 13);
        assertNull(window.finish(13));
        window.offer("Your Vorkath kill count is: 45.", false, 14);
        window.clear();
        window.offer("Fight duration: 1:36.01", false, 14);
        assertNull(window.finish(14));
    }
}
