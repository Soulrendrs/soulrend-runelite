package com.soulrend;

import org.junit.Test;
import static org.junit.Assert.*;

public class BingoBannerQueueTest {
    private static BingoBannerQueue.Card card(String tile, String detail, boolean completed) {
        return new BingoBannerQueue.Card(tile, "Coins", "Team", detail, completed);
    }

    @Test
    public void oneVisibleCardAndSameTileUpdatesCoalesce() {
        var queue = new BingoBannerQueue();
        var first = card("1:4", "2 / 5", false);
        var latest = card("1:4", "4 / 5", false);
        var other = card("1:7", "1 / 5", false);
        queue.offer(first);
        assertSame(first, queue.current(10L));
        queue.offer(other);
        queue.offer(latest);
        assertSame(latest, queue.current(100L));
        assertSame(other, queue.current(3511L));
    }

    @Test
    public void completionSupersedesPartialAndCannotBeOverwritten() {
        var queue = new BingoBannerQueue();
        var partial = card("1:4", "2 / 5", false);
        var complete = card("1:4", "By: Teammate", true);
        queue.offer(partial);
        assertSame(partial, queue.current(10L));
        assertTrue(queue.offer(complete));
        assertFalse(queue.offer(partial));
        assertSame(complete, queue.current(20L));
        assertNull(queue.current(3521L));
    }

    @Test
    public void completionSurvivesBusyQueue() {
        var queue = new BingoBannerQueue();
        for (int i = 0; i < 8; i++) queue.offer(card("1:" + i, "partial", false));
        var completion = card("2:1", "By: Teammate", true);
        assertTrue(queue.offer(completion));
        for (int i = 0; i < 7; i++) assertFalse(queue.current(1L + i * 3500).completed);
        assertSame(completion, queue.current(24501L));
        queue.clear();
        assertNull(queue.current(24502L));
    }
}
