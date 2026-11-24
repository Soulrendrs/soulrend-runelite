package com.soulrend;

import com.soulrend.BingoApiClient.CompletionFeed;
import com.soulrend.BingoApiClient.ConfirmedTile;
import com.soulrend.BingoApiClient.EventSummary;
import com.soulrend.BingoApiClient.ProgressTile;
import com.soulrend.BingoApiClient.UnlockedTile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Diffs the signed-in player's team board, independently of automatic evidence capture. */
final class BingoCompletionMonitor {
    static final String TITLE = "Soulrend Bingo";
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
        final Map<Integer, ProgressTile> progress = new HashMap<>();
        boolean progressInitialized;
        Boolean teamFinished;
        boolean finishAnnounced;
        boolean unlockInitialized;
        final Set<Integer> seenUnlocked = new HashSet<>();

        Board(long participation) {
            this.participation = participation;
        }
    }

    private final SoulrendPlugin plugin;
    private final Map<Integer, Board> boards = new HashMap<>();
    private final Map<Integer, Set<Integer>> uploadedCompletions = new HashMap<>();
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
        uploadedCompletions.clear();
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
                && board.seen.add(tile.id) && !baseline
                && !uploadedCompletions.getOrDefault(event, Set.of()).contains(tile.id)) added.add(tile);
        }
        return added;
    }

    synchronized boolean rememberUploadedNpc(int event, ConfirmedTile tile) {
        if (tile == null || !tile.self || tile.id <= 0 || tile.name == null || tile.name.isBlank()) return false;
        Board board = boards.get(event);
        if (board != null && board.seen.contains(tile.id)) return false;
        return uploadedCompletions.computeIfAbsent(event, id -> new HashSet<>()).add(tile.id);
    }

    void announceUploadedNpc(EventSummary event, ConfirmedTile tile, String session, String character) {
        if (!current(session, character) || !permits(true)) return;
        final Scope origin;
        synchronized (this) {
            origin = scope;
            if (origin == null || !session.equals(origin.session) || !rememberUploadedNpc(event.id, tile)) return;
        }
        plugin.clientThread.invokeLater(() -> {
            if (!current(session, character) || !Rsn.same(character, plugin.getLocalPlayerName())) return;
            synchronized (this) {
                if (scope != origin) return;
            }
            show(event, tile);
        });
    }

    synchronized List<ProgressTile> observeProgress(int event, CompletionFeed feed) {
        var board = boards.get(event);
        if (board == null || feed == null || board.participation != feed.participation) return List.of();
        var updated = new ArrayList<ProgressTile>();
        if (feed.progress != null) for (var tile : feed.progress) {
            if (tile == null || tile.id <= 0 || tile.revision <= 0 || tile.value <= 0 || tile.target <= 0
                || tile.name == null || tile.name.isBlank()) continue;
            var before = board.progress.get(tile.id);
            if (before != null && (tile.revision < before.revision
                || tile.revision == before.revision && tile.value == before.value)) continue;
            board.progress.put(tile.id, tile);
            if (board.progressInitialized) updated.add(tile);
        }
        board.progressInitialized = true;
        return updated;
    }

    synchronized boolean observeTeamFinish(int event, CompletionFeed feed) {
        var board = boards.get(event);
        if (board == null || feed == null || board.participation != feed.participation
            || feed.team_finished == null) return false;
        boolean changed = !board.finishAnnounced && Boolean.FALSE.equals(board.teamFinished) && feed.team_finished;
        board.teamFinished = feed.team_finished;
        if (changed) board.finishAnnounced = true;
        return changed;
    }

    synchronized List<UnlockedTile> observeUnlocks(int event, CompletionFeed feed) {
        var board = boards.get(event);
        if (board == null || feed == null || feed.completed == null
            || board.participation != feed.participation || feed.unlocked == null) return List.of();
        var added = new ArrayList<UnlockedTile>();
        for (var tile : feed.unlocked) {
            if (tile != null && tile.id > 0 && tile.name != null && !tile.name.isBlank()
                && board.seenUnlocked.add(tile.id) && board.unlockInitialized
                && !board.seen.contains(tile.id)) added.add(tile);
        }
        board.unlockInitialized = true;
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
            final List<ProgressTile> progress;
            final boolean teamFinished;
            final List<UnlockedTile> unlocked;
            synchronized (this) {
                if (scope != origin) return;
                additions = observe(event.id, feed);
                progress = observeProgress(event.id, feed);
                teamFinished = observeTeamFinish(event.id, feed);
                unlocked = observeUnlocks(event.id, feed);
            }
            if (teamFinished || !additions.isEmpty() || !progress.isEmpty() || !unlocked.isEmpty()) plugin.clientThread.invokeLater(() -> {
                if (!current(session, character) || !Rsn.same(character, plugin.getLocalPlayerName())) return;
                synchronized (this) {
                    if (scope != origin) return;
                }
                for (var tile : additions) show(event, tile);
                for (var tile : progress) showProgress(event, tile);
                if (teamFinished) showTeamFinish(event);
                else if (!unlocked.isEmpty()) showUnlocks(event, unlocked);
            });
        }
        synchronized (this) {
            if (scope == origin) {
                boards.keySet().retainAll(active);
                uploadedCompletions.keySet().retainAll(active);
            }
        }
    }

    private boolean current(String session, String character) {
        return enabled() && Rsn.same(character, plugin.apiClient.getCurrentRsn())
            && session.equals(DiskFiles.fingerprint(plugin.apiClient.playerToken));
    }

    private boolean permits(boolean self) {
        return self ? plugin.config.selfTileBanner() : plugin.config.teamCompletionBanner();
    }

    private void showUnlocks(EventSummary event, List<UnlockedTile> unlocked) {
        if (!plugin.config.teamCompletionBanner() || !plugin.config.bingoUnlockPopups()) return;
        var first = unlocked.get(0);
        String detail = unlocked.size() == 1 ? "Now available on your board"
            : unlocked.size() == 2 ? unlocked.get(1).name
            : unlocked.get(1).name + " +" + (unlocked.size() - 2) + " more";
        plugin.clogBanner.bingoCards.offer(new BingoBannerQueue.Card(event.id + ":unlock:" + first.id,
            first.name, event.team, detail, false, () -> {}, first.item_id, first.icon_path, first.skill));
        var names = new ArrayList<String>();
        for (var tile : unlocked) names.add(tile.name);
        plugin.sendChatMessage(ChatPalette.Tone.DONE, "Unlocked: " + String.join(", ", names));
    }

    private void showTeamFinish(EventSummary event) {
        if (!plugin.config.teamCompletionBanner()) return;
        String team = event.team == null ? "Your team" : event.team;
        plugin.clogBanner.bingoCards.offer(new BingoBannerQueue.Card(event.id + ":team-finished",
            event.name, team, "All objectives confirmed", true,
            () -> plugin.bannerSound.playBingoCue(BannerSoundService.BingoCue.TEAM_BINGO_COMPLETE),
            0, null, null));
        plugin.sendChatMessage(ChatPalette.Tone.DONE, team + " completed " + event.name + "!");
    }

    private void show(EventSummary event, ConfirmedTile tile) {
        if (!permits(tile.self)) return;
        var contributor = tile.contributor == null || tile.contributor.isBlank()
            ? "Team completion" : "By: " + tile.contributor;
        var detail = tile.value == null || tile.target == null ? contributor
            : String.format(Locale.ROOT, "%,d / %,d · %s", tile.value, tile.target, contributor);
        var card = new BingoBannerQueue.Card(event.id + ":" + tile.id, tile.name, event.team,
            detail, true, () -> plugin.bannerSound.playBingoCue(BannerSoundService.BingoCue.COMPLETION),
            tile.item_id, tile.icon_path, tile.skill, false, tile.self, tile.value, tile.target,
            0, tile.contributor, null);
        plugin.clogBanner.bingoCards.offer(card);
        plugin.sendChatMessage(ChatPalette.Tone.DONE, "Bingooooo! " + tile.name + " · " + contributor);
    }

    private void showProgress(EventSummary event, ProgressTile tile) {
        if (!permits(tile.self) || !tile.self && "solo".equals(tile.scope)) return;
        var progress = String.format(Locale.ROOT, "%,d / %,d %s", tile.value, tile.target,
            "kills".equals(tile.unit) ? "kills" : "items");
        var contributor = tile.contributor == null ? "Progress recorded" : "Latest: " + tile.contributor;
        if (tile.quantity > 0) contributor += "  +" + tile.quantity;
        if ("solo".equals(tile.scope)) contributor += "  ·  Best solo";
        var cue = tile.self ? BannerSoundService.BingoCue.SELF_PROGRESS
            : BannerSoundService.BingoCue.TEAM_PROGRESS;
        plugin.clogBanner.bingoCards.offer(new BingoBannerQueue.Card(event.id + ":" + tile.id,
            tile.name, event.team, progress + " · " + contributor, false,
            () -> plugin.bannerSound.playBingoCue(cue), tile.item_id, tile.icon_path, null,
            false, tile.self, tile.value, tile.target, tile.quantity, tile.contributor, tile.scope));
    }
}
