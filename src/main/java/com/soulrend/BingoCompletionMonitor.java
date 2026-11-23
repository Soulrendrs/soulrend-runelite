package com.soulrend;

import com.soulrend.BingoApiClient.CompletionFeed;
import com.soulrend.BingoApiClient.ConfirmedTile;
import com.soulrend.BingoApiClient.EventSummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Diffs the signed-in player's team board, independently of automatic evidence capture. */
final class BingoCompletionMonitor {
    private static final String TITLE = "Soulrend Bingo";
    private static final int MAX_BOARDS = 5;

    private static final class Scope {
        final String session;
        final String character;

        Scope(String session, String character) {
            this.session = session;
            this.character = character;
        }
    }

    private static final class Board {
        final long participation;
        final Set<Integer> seen = new HashSet<>();

        Board(long participation) {
            this.participation = participation;
        }
    }

    private final SoulrendPlugin plugin;
    private final Map<Integer, Board> boards = new HashMap<>();
    private Scope scope;

    BingoCompletionMonitor(SoulrendPlugin plugin) {
        this.plugin = plugin;
    }

    boolean enabled() {
        return plugin.config != null && (plugin.config.selfTileBanner() || plugin.config.teamCompletionBanner())
            && plugin.apiClient != null && !plugin.apiClient.needsSignIn()
            && !plugin.apiClient.isAccountPaused() && BingoApiClient.CANONICAL_SITE.equals(BingoApiClient.site())
            && plugin.loggedIn() && !plugin.statsAreArtificial();
    }

    synchronized void clear() {
        if (scope == null) return;
        scope = null;
        boards.clear();
        if (plugin != null && plugin.clogBanner != null) {
            plugin.clientThread.invokeLater(() -> plugin.clogBanner.removeBanners(TITLE));
        }
    }

    synchronized void useScope(String session, String character) {
        String normalized = character.replace('_', ' ').strip().toLowerCase(Locale.ROOT);
        if (scope != null && session.equals(scope.session) && normalized.equals(scope.character)) return;
        clear();
        scope = new Scope(session, normalized);
    }

    synchronized List<ConfirmedTile> observe(int event, CompletionFeed feed) {
        if (feed == null || feed.completed == null) return List.of();
        if (feed.participation <= 0) {
            boards.remove(event);
            return List.of();
        }
        var board = boards.get(event);
        boolean baseline = board == null || board.participation != feed.participation;
        if (baseline) {
            board = new Board(feed.participation);
            boards.put(event, board);
        }
        var added = new ArrayList<ConfirmedTile>();
        for (var tile : feed.completed) {
            if (tile != null && tile.id > 0 && tile.name != null && !tile.name.isBlank()
                && board.seen.add(tile.id) && !baseline) added.add(tile);
        }
        return added;
    }

    void poll(List<EventSummary> events, String token, String session, String character) {
        if (!current(session, character)) return;
        useScope(session, character);
        final Scope origin;
        synchronized (this) { origin = scope; }

        var active = new HashSet<Integer>();
        for (var event : events) {
            if (event == null || event.id <= 0 || !"active".equals(event.status)
                || event.characters == null || event.characters.stream().noneMatch(name -> Rsn.same(character, name))
                || active.size() >= MAX_BOARDS || !active.add(event.id)) continue;
            if (!current(session, character)) return;
            var feed = plugin.apiClient.bingoCompletions(event.id, token, character);
            if (!current(session, character)) return;
            final List<ConfirmedTile> additions;
            synchronized (this) {
                if (scope != origin) return;
                additions = observe(event.id, feed);
            }
            if (!additions.isEmpty()) plugin.clientThread.invokeLater(() -> {
                if (!current(session, character) || !Rsn.same(character, plugin.getLocalPlayerName())) return;
                synchronized (this) {
                    if (scope != origin) return;
                }
                for (var tile : additions) show(tile);
            });
        }
        synchronized (this) {
            if (scope == origin) boards.keySet().retainAll(active);
        }
    }

    private boolean current(String session, String character) {
        return enabled() && Rsn.same(character, plugin.apiClient.getCurrentRsn())
            && session.equals(DiskFiles.fingerprint(plugin.apiClient.playerToken));
    }

    private void show(ConfirmedTile tile) {
        if (tile.self ? !plugin.config.selfTileBanner() : !plugin.config.teamCompletionBanner()) return;
        var heading = tile.self ? "You completed a tile!" : "Team tile complete!";
        var detail = !tile.self && tile.contributor != null && !tile.contributor.isBlank()
            ? tile.contributor + ": " + tile.name : tile.name;
        if (plugin.clogBanner.show(TITLE, heading, detail)) plugin.playBannerSound();
        plugin.sendChatMessage(ChatPalette.Tone.DONE, heading + " " + detail);
    }
}
