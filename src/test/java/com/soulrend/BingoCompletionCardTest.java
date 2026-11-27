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

    @Test public void typedProgressDoesNotInventUnits() {
        for (String unit : new String[]{"GP", "sets", "unique items", "XP", "KC", "kills", "items", "<html>invalid"}) {
            var card = new BingoBannerQueue.Card("1:2", "Objective", "Team", "", false,
                () -> {}, 0, null, null, false, true, 5_000_000_000L, 10_000_000_000L,
                0, null, "solo", unit);
            assertEquals("5,000,000,000 / 10,000,000,000" + BingoApiClient.unitSuffix(unit),
                BingoCompletionCard.progressLabel(card));
            var image = new BufferedImage(BingoCompletionCard.WIDTH, BingoCompletionCard.HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            try { BingoCompletionCard.paint(g, card, 0, false); }
            finally { g.dispose(); }
        }
    }

    @Test public void milestonesHaveTheirOwnHeadingsAndDurations() {
        var finish = new BingoBannerQueue.Card("1:team-finished", "Event", "Team",
            "All objectives confirmed", true, () -> {}, 0, null, null);
        var unlock = new BingoBannerQueue.Card("1:unlock:2", "Next objective", "Team",
            "Now available", false, () -> {}, 0, null, null);
        assertEquals("TEAM BINGO COMPLETE!", BingoCompletionCard.heading(finish));
        assertEquals("Objective unlocked", BingoCompletionCard.heading(unlock));
        assertNotEquals(BingoCompletionCard.accent(finish), BingoCompletionCard.accent(card(true, true, false, 1L, 1L)));
        assertEquals(7000L, finish.holdMs());
        assertEquals(5000L, unlock.holdMs());
        var queue = new BingoBannerQueue();
        queue.offer(card(true, false, false, 1L, 2L));
        queue.offer(unlock);
        queue.offer(finish);
        assertSame(finish, queue.current(1L));
        assertSame(unlock, queue.current(7001L));
    }

    @Test public void eachVariantRendersWithoutAnimationsOrIcon() {
        var cards = new BingoBannerQueue.Card[]{
            card(true, false, false, 3L, 5L), card(false, false, false, 4L, 5L),
            card(true, true, false, 5L, 5L), card(true, false, true, null, null),
            new BingoBannerQueue.Card("1:team-finished", "Event", "Team", "All objectives confirmed", true, () -> {}, 0, null, null),
            new BingoBannerQueue.Card("1:unlock:2", "Next objective", "Team", "Now available", false, () -> {}, 0, null, null)};
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
