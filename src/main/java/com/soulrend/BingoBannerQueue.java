package com.soulrend;

import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.Icon;

/** Keeps one visible bingo alert and one latest queued update per event tile. */
final class BingoBannerQueue {
    private static final int MAX_WAITING = 8;
    private static final long HOLD_MS = 3500;

    static final class Card {
        final String key, objective, team, detail;
        final boolean completed;
        final Runnable onShown;
        final int itemId;
        final String iconPath, skill;
        volatile Icon icon;

        Card(String key, String objective, String team, String detail, boolean completed,
             Runnable onShown, int itemId, String iconPath, String skill) {
            this.key = key;
            this.objective = objective;
            this.team = team;
            this.detail = detail;
            this.completed = completed;
            this.onShown = onShown;
            this.itemId = itemId;
            this.iconPath = iconPath;
            this.skill = skill;
        }
    }

    private final Deque<Card> waiting = new ArrayDeque<>();
    private Card visible;
    private long started;

    boolean offer(Card next) {
        if (waiting.stream().anyMatch(card -> card.key.equals(next.key) && card.completed)) return false;
        if (visible != null && visible.key.equals(next.key)) {
            if (visible.completed) return false;
            if (!next.completed) {
                next.icon = visible.icon;
                visible = next;
                return true;
            }
        }
        waiting.removeIf(card -> card.key.equals(next.key));
        if (waiting.size() >= MAX_WAITING) {
            var partial = waiting.stream().filter(card -> !card.completed).findFirst().orElse(null);
            if (partial == null && !next.completed) return false;
            if (partial != null) waiting.remove(partial);
        }
        waiting.addLast(next);
        return true;
    }

    Card current(long now) {
        if (visible == null || started != 0 && now - started >= HOLD_MS) {
            visible = waiting.stream().filter(card -> card.completed).findFirst().orElse(null);
            if (visible == null) visible = waiting.pollFirst();
            else waiting.remove(visible);
            started = 0;
        }
        if (visible != null && started == 0) {
            started = now;
            visible.onShown.run();
        }
        return visible;
    }

    long elapsed(long now) {
        return Math.max(0, now - started);
    }

    void clear() {
        visible = null;
        waiting.clear();
        started = 0;
    }
}
