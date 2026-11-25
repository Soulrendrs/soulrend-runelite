package com.soulrend;

import java.awt.image.BufferedImage;
import com.soulrend.PendingSubmissionStore.PendingSubmission;

/** Explicitly unconfirmed metadata on a copy of the captured gameplay frame. */
final class EvidenceCaptureCard {
    private EvidenceCaptureCard() {}

    static BingoBannerQueue.Card forProof(PendingSubmission proof) {
        String kind;
        switch (proof.evidenceKind) {
            case "npc_kill": kind = "NPC kill"; break;
            case "drop": kind = "Item drop"; break;
            case "gain": kind = "Skilling gain"; break;
            case "ca": kind = "Combat achievement"; break;
            default: kind = "Objective evidence";
        }
        return new BingoBannerQueue.Card(proof.eventId + ":" + proof.tileId,
            (proof.evidenceName == null || proof.evidenceName.isBlank()
                ? kind + " #" + proof.evidenceId : proof.evidenceName.strip()) + " (+" + proof.amount + ")",
            proof.objectiveName == null || proof.objectiveName.isBlank() ? "Bingo objective" : proof.objectiveName.strip(), "Awaiting server confirmation", false,
            () -> {}, "drop".equals(proof.evidenceKind) || "gain".equals(proof.evidenceKind)
                ? proof.evidenceId : 0, proof.evidenceIconPath, null, true);
    }

    static BufferedImage annotate(BufferedImage frame, BingoBannerQueue.Card card) {
        var copy = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = copy.createGraphics();
        try {
            g.drawImage(frame, 0, 0, null);
            double scale = Math.min(0.75, Math.min((frame.getWidth() - 4.0) / BingoCompletionCard.WIDTH,
                (frame.getHeight() - 4.0) / BingoCompletionCard.HEIGHT));
            if (scale > 0) {
                g.translate((frame.getWidth() - BingoCompletionCard.WIDTH * scale) / 2.0, 2);
                g.scale(scale, scale);
                BingoCompletionCard.paint(g, card, 180, false);
            }
        } finally {
            g.dispose();
        }
        return copy;
    }
}
