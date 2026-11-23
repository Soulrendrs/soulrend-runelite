package com.soulrend;

import java.util.ArrayDeque;
import java.util.Deque;

/** Keeps one visible bingo alert and one latest queued update per event tile. */
final class BingoBannerQueue {
    private static final int MAX_WAITING = 8;
    private static final long HOLD_MS = 3500;

    static final class Card {
        final String key, objective, team, detail;
        final boolean completed;

        Card(String key, String objective, String team, String detail, boolean completed) {
            this.key = key;
            this.objective = objective;
            this.team = team;
            this.detail = detail;
            this.completed = completed;
        }
    }

    private final Deque<Card> waiting = new ArrayDeque<>();
    private Card visible;
    private long started;

    boolean offer(Card next) {
        if (visible != null && visible.key.equals(next.key)) {
            if (visible.completed) return false;
            visible = next;
            if (next.completed) started = 0;
            return true;
        }
        if (waiting.stream().anyMatch(card -> card.key.equals(next.key) && card.completed)) return false;
        waiting.removeIf(card -> card.key.equals(next.key));
        if (waiting.size() >= MAX_WAITING) {
            var partial = waiting.stream().filter(card -> !card.completed).findFirst().orElse(null);
            if (partial == null) return false;
            waiting.remove(partial);
        }
        waiting.addLast(next);
        return true;
    }

    Card current(long now) {
        if (visible == null || started != 0 && now - started >= HOLD_MS) {
            visible = waiting.pollFirst();
            started = 0;
        }
        if (visible != null && started == 0) started = now;
        return visible;
    }

    void clear() {
        visible = null;
        waiting.clear();
        started = 0;
    }
}
