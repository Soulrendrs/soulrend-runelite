package com.soulrend;

import org.junit.Test;
import static org.junit.Assert.*;

public class BingoBannerQueueTest {
    private static BingoBannerQueue.Card card(String tile, String detail, boolean completed) {
        return new BingoBannerQueue.Card(tile, "Coins", "Team", detail, completed, () -> {}, 0, null, null);
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
        assertSame(other, queue.current(3011L));
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
        assertSame(complete, queue.current(5019L));
        assertNull(queue.current(5020L));
    }

    @Test
    public void completionSurvivesBusyQueue() {
        var queue = new BingoBannerQueue();
        for (int i = 0; i < 8; i++) queue.offer(card("1:" + i, "partial", false));
        var completion = card("2:1", "By: Teammate", true);
        assertTrue(queue.offer(completion));
        assertSame(completion, queue.current(1L));
        assertFalse(queue.current(5001L).completed);
        queue.clear();
        assertNull(queue.current(5002L));
    }
    @Test
    public void confirmedProgressReplacesEvidenceAndCannotBeOverwrittenByLateCapture() {
        var queue = new BingoBannerQueue();
        var evidence = new BingoBannerQueue.Card("1:4", "Coins", null, "Awaiting server confirmation",
            false, () -> {}, 0, null, null, true);
        var confirmed = card("1:4", "2 / 5", false);
        queue.offer(evidence);
        assertSame(evidence, queue.current(1));
        assertTrue(queue.offer(confirmed));
        assertSame(confirmed, queue.current(2));
        assertFalse(queue.offer(evidence));
        assertSame(confirmed, queue.current(3));
    }

    @Test
    public void everyCompletionIsShownAndStartsAudioOnce() {
        var queue = new BingoBannerQueue();
        var starts = new java.util.concurrent.atomic.AtomicInteger();
        for (int i = 0; i < 12; i++) {
            assertTrue(queue.offer(new BingoBannerQueue.Card("3:" + i, "Coins", "Team",
                "Complete", true, starts::incrementAndGet, 0, null, null)));
        }
        assertEquals(0, starts.get());
        for (int i = 0; i < 12; i++) {
            long now = 1 + i * 5000L;
            assertEquals("3:" + i, queue.current(now).key);
            queue.current(now + 100);
            assertEquals(i + 1, starts.get());
        }
    }
    @Test
    public void confirmedCompletionInterruptsUnrelatedProgress() {
        var queue = new BingoBannerQueue();
        var partial = card("1:1", "1 / 5", false);
        var completion = card("1:2", "Complete", true);
        queue.offer(partial);
        assertSame(partial, queue.current(1));
        queue.offer(completion);
        assertSame(completion, queue.current(2));
        var later = card("1:3", "Complete", true);
        queue.offer(later);
        assertSame(completion, queue.current(3));
        assertSame(later, queue.current(5002));
    }

}
