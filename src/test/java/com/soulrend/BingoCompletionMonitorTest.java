package com.soulrend;

import com.soulrend.BingoApiClient.CompletionFeed;
import com.soulrend.BingoApiClient.ConfirmedTile;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BingoCompletionMonitorTest {
    private static ConfirmedTile tile(int id, String name) {
        var tile = new ConfirmedTile();
        tile.id = id;
        tile.name = name;
        return tile;
    }

    private static CompletionFeed feed(long participation, ConfirmedTile... tiles) {
        var feed = new CompletionFeed();
        feed.participation = participation;
        feed.completed = List.of(tiles);
        return feed;
    }

    @Test
    public void onlyNewConfirmedTilesAppearAndOutagesDoNotReplayHistory() {
        var monitor = new BingoCompletionMonitor(null);
        var historical = tile(1, "Previous tile");
        var latest = tile(2, "Fresh tile");
        assertTrue(monitor.observe(5, feed(10, historical)).isEmpty());
        assertTrue(monitor.observe(5, null).isEmpty());
        assertEquals(List.of(latest), monitor.observe(5, feed(10, historical, latest, latest)));
        assertTrue(monitor.observe(5, feed(10, historical)).isEmpty());
        assertTrue(monitor.observe(5, feed(10, historical, latest)).isEmpty());
    }

    @Test
    public void newTeamsAndSignInScopesStartWithTheirOwnSilentBaseline() {
        var monitor = new BingoCompletionMonitor(null);
        var old = tile(1, "Previous tile");
        var added = tile(2, "New tile");
        monitor.useScope("session", "My_Account");
        assertTrue(monitor.observe(5, feed(10, old)).isEmpty());
        monitor.useScope("session", "my account");
        assertEquals(List.of(added), monitor.observe(5, feed(10, old, added)));
        assertTrue(monitor.observe(5, feed(20, tile(3, "Other team"))).isEmpty());
        monitor.useScope("new session", "My Account");
        assertTrue(monitor.observe(5, feed(20, old, added)).isEmpty());
        monitor.clear();
        assertTrue(monitor.observe(5, feed(20, old, added)).isEmpty());
    }
}
