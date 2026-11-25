package com.soulrend;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TeamBingoFinishTest {
    @Test public void onlyConfirmedTransitionCelebrates() {
        var monitor = new BingoCompletionMonitor(null);
        var feed = new BingoApiClient.CompletionFeed();
        feed.participation = 1;
        feed.completed = List.of();
        monitor.observe(10, feed);
        assertFalse(monitor.observeTeamFinish(10, feed));
        feed.team_finished = false;
        assertFalse(monitor.observeTeamFinish(10, feed));
        feed.team_finished = true;
        assertTrue(monitor.observeTeamFinish(10, feed));
        assertFalse(monitor.observeTeamFinish(10, feed));
        feed.participation = 2;
        monitor.observe(10, feed);
        assertFalse(monitor.observeTeamFinish(10, feed));
    }

    @Test public void teamCelebrationHasDistinctHeadingAndPriority() {
        var queue = new BingoBannerQueue();
        var tile = new BingoBannerQueue.Card("1:2", "Goblin", "Team", "Done", true,
            () -> {}, 0, null, null);
        var finish = new BingoBannerQueue.Card("1:team-finished", "Weekend bingo", "Team",
            "Completed!", true, () -> {}, 0, null, null);
        queue.offer(tile);
        queue.offer(finish);
        assertEquals("TEAM BINGO COMPLETE!", BingoCompletionCard.heading(finish));
        assertEquals(7000L, finish.holdMs());
        assertSame(finish, queue.current(100));
        assertSame(tile, queue.current(7100));
    }
}
