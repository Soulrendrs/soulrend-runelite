package com.soulrend;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Show one local logout reminder per event and scheduled start. */
final class BingoStartReminder {
    private static final long WINDOW_SECONDS = 10 * 60L;
    static final String TITLE = "Bingo Start Reminder";

    // Only read and written on the RuneLite client thread.
    private final Map<Integer, Long> announcedStarts = new HashMap<>();

    boolean claim(BingoApiClient.EventSummary event, long now) {
        if (event == null || event.id <= 0 || !"upcoming".equals(event.status)
            || event.start_at_epoch <= now || event.start_at_epoch > now + WINDOW_SECONDS) return false;
        announcedStarts.values().removeIf(start -> start <= now);
        Long previous = announcedStarts.put(event.id, event.start_at_epoch);
        return previous == null || previous.longValue() != event.start_at_epoch;
    }

    static boolean enabled(SoulrendPlugin plugin) {
        return plugin.execLive() && plugin.apiClient != null && plugin.loggedIn() && !plugin.statsAreArtificial()
            && BingoApiClient.CANONICAL_SITE.equals(BingoApiClient.site()) && !plugin.apiClient.needsSignIn()
            && !plugin.apiClient.isAccountPaused();
    }

    void showDue(SoulrendPlugin plugin, List<BingoApiClient.EventSummary> events,
                 String token, String character) {
        if (!enabled(plugin)) return;
        plugin.clientThread.invokeLater(() -> {
            if (!enabled(plugin) || !token.equals(plugin.apiClient.playerToken)
                || !Rsn.same(character, plugin.apiClient.getCurrentRsn())
                || !Rsn.same(character, plugin.getLocalPlayerName())) return;
            long now = Instant.now().getEpochSecond();
            for (var event : events) {
                if (event == null || event.characters == null
                    || event.characters.stream().noneMatch(name -> Rsn.same(name, character))
                    || !claim(event, now)) continue;
                plugin.clogBanner.show(TITLE, "Bingo starts soon", "Log out for Wise Old Man");
                plugin.sendChatMessage(ChatPalette.Tone.WARN, (event.name == null ? "Your bingo" : event.name)
                    + " starts soon. Log out of OSRS before the start so Wise Old Man can capture fresh XP and boss KC.");
            }
        });
    }
}
