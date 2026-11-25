package com.soulrend;

import com.soulrend.BingoApiClient.CompletionFeed;
import com.soulrend.BingoApiClient.ConfirmedTile;
import com.soulrend.BingoApiClient.UnlockedTile;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

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

    @Test
    public void approvedNpcReceiptDoesNotNeedAFeedBaselineOrRepeatInTheFeed() {
        var monitor = new BingoCompletionMonitor(null);
        monitor.useScope("session", "My account");
        var npc = tile(7, "Goblin");
        npc.self = true;
        assertTrue(monitor.rememberUploadedNpc(5, npc));
        assertTrue(monitor.observe(5, feed(10)).isEmpty());
        assertTrue(monitor.observe(5, feed(10, npc)).isEmpty());
        assertTrue(!monitor.rememberUploadedNpc(5, npc));
        var teammate = tile(8, "Teammate's tile");
        assertTrue(!monitor.rememberUploadedNpc(5, teammate));
        assertEquals(List.of(teammate), monitor.observe(5, feed(10, npc, teammate)));
    }

    private static UnlockedTile unlocked(int id, String name) {
        var tile = new UnlockedTile();
        tile.id = id;
        tile.name = name;
        return tile;
    }

    @Test public void unlocksAreBaselinedDeduplicatedAndScoped() {
        var monitor = new BingoCompletionMonitor(null);
        var root = unlocked(1, "Root");
        var child = unlocked(2, "Child");
        var finished = unlocked(3, "Already completed");
        var initial = feed(10);
        monitor.observe(5, initial);
        assertTrue(monitor.observeUnlocks(5, initial).isEmpty()); // older API
        initial.unlocked = List.of(root);
        assertTrue(monitor.observeUnlocks(5, initial).isEmpty());
        var next = feed(10, tile(3, "Already completed"));
        next.unlocked = List.of(root, child, child, finished);
        monitor.observe(5, next);
        assertEquals(List.of(child), monitor.observeUnlocks(5, next));
        assertTrue(monitor.observeUnlocks(5, null).isEmpty());
        assertTrue(monitor.observeUnlocks(5, next).isEmpty());
        var relock = feed(10);
        relock.unlocked = List.of(root);
        assertTrue(monitor.observeUnlocks(5, relock).isEmpty());
        assertTrue(monitor.observeUnlocks(5, next).isEmpty());
        var otherTeam = feed(20);
        otherTeam.unlocked = List.of(unlocked(4, "Other team"));
        monitor.observe(5, otherTeam);
        assertTrue(monitor.observeUnlocks(5, otherTeam).isEmpty());
    }

    @Test public void teamFinishRequiresObservedTransitionAndDoesNotReplay() {
        var monitor = new BingoCompletionMonitor(null);
        var snapshot = feed(10);
        monitor.observe(5, snapshot);
        assertFalse(monitor.observeTeamFinish(5, snapshot));
        snapshot.team_finished = true;
        assertFalse(monitor.observeTeamFinish(5, snapshot));
        snapshot.team_finished = false;
        assertFalse(monitor.observeTeamFinish(5, snapshot));
        snapshot.team_finished = true;
        assertTrue(monitor.observeTeamFinish(5, snapshot));
        assertFalse(monitor.observeTeamFinish(5, snapshot));
        snapshot.team_finished = false;
        assertFalse(monitor.observeTeamFinish(5, snapshot));
        snapshot.team_finished = true;
        assertFalse(monitor.observeTeamFinish(5, snapshot));
    }
}
