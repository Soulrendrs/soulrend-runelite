package com.soulrend;

import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class EvidenceCaptureCardTest {
    @Test public void allEvidenceKindsRemainUnconfirmed() {
        for (String kind : new String[]{"npc_kill", "drop", "gain", "ca"}) {
            var proof = new PendingSubmissionStore.PendingSubmission();
            proof.evidenceKind = kind;
            proof.evidenceId = 42;
            proof.eventId = 1;
            proof.tileId = 2;
            proof.amount = 1;
            var card = EvidenceCaptureCard.forProof(proof);
            assertTrue(card.evidence);
            assertFalse(card.completed);
            assertEquals("1:2", card.key);
            assertEquals("Awaiting server confirmation", card.detail);
            assertTrue(card.objective.contains("#42"));
        }
    }

    @Test public void namedNpcEvidenceShowsNameRatherThanId() {
        var proof = new PendingSubmissionStore.PendingSubmission();
        proof.evidenceKind = "npc_kill";
        proof.evidenceId = 2790;
        proof.evidenceName = "Cow (level 2)";
        proof.amount = 1;
        var card = EvidenceCaptureCard.forProof(proof);
        assertEquals("Cow (level 2) (+1)", card.objective);
        assertFalse(card.completed);
        assertEquals(2790, proof.evidenceId);
    }

    @Test public void annotationDoesNotMutateOriginalFrame() {
        var frame = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        var proof = new PendingSubmissionStore.PendingSubmission();
        proof.evidenceKind = "npc_kill";
        var copy = EvidenceCaptureCard.annotate(frame, EvidenceCaptureCard.forProof(proof));
        assertNotSame(frame, copy);
        assertEquals(frame.getWidth(), copy.getWidth());
        assertEquals(frame.getHeight(), copy.getHeight());
        assertEquals(0xff000000, frame.getRGB(320, 30));
        assertNotEquals(frame.getRGB(320, 30), copy.getRGB(320, 30));
        assertEquals(frame.getRGB(10, 400), copy.getRGB(10, 400));
        assertEquals(frame.getRGB(320, 100), copy.getRGB(320, 100));
    }

    @Test public void confirmedPopupImmediatelyReplacesVisibleEvidenceCard() {
        var queue = new BingoBannerQueue();
        var proof = new PendingSubmissionStore.PendingSubmission();
        proof.evidenceKind = "npc_kill";
        var evidence = EvidenceCaptureCard.forProof(proof);
        assertTrue(queue.offer(evidence));
        assertSame(evidence, queue.current(100));
        var complete = new BingoBannerQueue.Card(evidence.key, "Goblin", null, "Confirmed",
            true, () -> {}, 0, null, null);
        assertTrue(queue.offer(complete));
        assertSame(complete, queue.current(200));
    }
}
