package com.soulrend;

import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class BingoCompletionCardTest {
    private static BingoBannerQueue.Card card(boolean self, boolean completed, boolean evidence, Long value, Long target) {
        return new BingoBannerQueue.Card("1:2", "An extremely long objective name that must remain inside the card",
            "Team", "Awaiting server confirmation", completed, () -> {}, 0, null, null, evidence,
            self, value, target, 3, "Teammate", "team");
    }

    @Test public void visualVariantsReflectServerState() {
        var own = card(true, false, false, 3L, 5L);
        var team = card(false, false, false, 4L, 5L);
        var done = card(true, true, false, 6L, 5L);
        var evidence = card(true, false, true, 5L, 5L);
        assertEquals("Objective progress", BingoCompletionCard.heading(own));
        assertEquals("Teammate progress", BingoCompletionCard.heading(team));
        assertEquals("BINGOOOOO!", BingoCompletionCard.heading(done));
        assertEquals("Evidence captured", BingoCompletionCard.heading(evidence));
        assertNotEquals(BingoCompletionCard.accent(own), BingoCompletionCard.accent(team));
        assertNotEquals(BingoCompletionCard.accent(own), BingoCompletionCard.accent(done));
        assertEquals("3 / 5", BingoCompletionCard.progressLabel(own));
        assertEquals(0.6, own.fraction(), 0.0001);
        assertEquals(1.0, done.fraction(), 0.0001);
        assertFalse(evidence.hasProgress());
        assertEquals("", BingoCompletionCard.progressLabel(evidence));
        assertFalse(card(true, false, false, 1L, 0L).hasProgress());
    }

    @Test public void eachVariantRendersWithoutAnimationsOrIcon() {
        var cards = new BingoBannerQueue.Card[]{
            card(true, false, false, 3L, 5L), card(false, false, false, 4L, 5L),
            card(true, true, false, 5L, 5L), card(true, false, true, null, null)};
        for (var card : cards) {
            var image = new BufferedImage(BingoCompletionCard.WIDTH, BingoCompletionCard.HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try {
                BingoCompletionCard.paint(graphics, card, 0, false);
            } finally {
                graphics.dispose();
            }
            assertEquals(255, image.getRGB(160, 60) >>> 24);
        }
    }
}
