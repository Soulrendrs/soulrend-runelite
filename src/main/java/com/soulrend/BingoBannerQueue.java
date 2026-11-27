package com.soulrend;

import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.Icon;

/** Keeps one visible bingo alert and one latest queued update per event tile. */
final class BingoBannerQueue {
    private static final int MAX_WAITING = 8;
    static final long PROGRESS_MS = 3000;
    static final long COMPLETION_MS = 5000;

    static final class Card {
        final String key, objective, team, detail;
        final boolean completed;
        final boolean evidence;
        final boolean self;
        final Long value, target;
        final long quantity;
        final String contributor, scope, unit;
        final Runnable onShown;
        final int itemId;
        final String iconPath, skill;
        volatile Icon icon;

        Card(String key, String objective, String team, String detail, boolean completed,
             Runnable onShown, int itemId, String iconPath, String skill) {
            this(key, objective, team, detail, completed, onShown, itemId, iconPath, skill, false);
        }

        Card(String key, String objective, String team, String detail, boolean completed,
             Runnable onShown, int itemId, String iconPath, String skill, boolean evidence) {
            this(key, objective, team, detail, completed, onShown, itemId, iconPath, skill,
                evidence, true, null, null, 0, null, null);
        }

        Card(String key, String objective, String team, String detail, boolean completed,
             Runnable onShown, int itemId, String iconPath, String skill, boolean evidence,
             boolean self, Long value, Long target, long quantity, String contributor, String scope) {
            this(key, objective, team, detail, completed, onShown, itemId, iconPath, skill,
                evidence, self, value, target, quantity, contributor, scope, null);
        }

        Card(String key, String objective, String team, String detail, boolean completed,
             Runnable onShown, int itemId, String iconPath, String skill, boolean evidence,
             boolean self, Long value, Long target, long quantity, String contributor, String scope, String unit) {
            this.unit = unit;
            this.evidence = evidence;
            this.self = self;
            this.value = value;
            this.target = target;
            this.quantity = quantity;
            this.contributor = contributor;
            this.scope = scope;
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

        boolean hasProgress() { return !evidence && value != null && target != null && target > 0; }
        double fraction() { return hasProgress() ? Math.max(0, Math.min(1, value / (double) target)) : 0; }
        boolean teamFinished() { return key.endsWith(":team-finished"); }
        boolean unlocked() { return key.contains(":unlock:"); }
        long holdMs() { return teamFinished() ? 7000 : completed || unlocked() ? COMPLETION_MS : PROGRESS_MS; }
    }

    private final Deque<Card> waiting = new ArrayDeque<>();
    private Card visible;
    private long started;

    boolean offer(Card next) {
        if (waiting.stream().anyMatch(card -> card.key.equals(next.key)
            && (card.completed || next.evidence && !card.evidence))) return false;
        if (visible != null && visible.key.equals(next.key)) {
            if (visible.completed || next.evidence && !visible.evidence) return false;
            if (visible.evidence && !next.evidence || next.completed) {
                if (next.icon == null && visible.itemId == next.itemId
                    && java.util.Objects.equals(visible.iconPath, next.iconPath)
                    && java.util.Objects.equals(visible.skill, next.skill)) next.icon = visible.icon;
                visible = next;
                started = 0;
                waiting.removeIf(card -> card.key.equals(next.key));
                return true;
            }
            if (!next.completed) {
                if (next.icon == null && visible.itemId == next.itemId
                    && java.util.Objects.equals(visible.iconPath, next.iconPath)
                    && java.util.Objects.equals(visible.skill, next.skill)) next.icon = visible.icon;
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
        // A confirmed win should not sit behind an ordinary progress card.
        // Keep existing celebrations and unlocks intact; audio remains serialized.
        if (next.completed && visible != null && !visible.completed && !visible.unlocked()) {
            visible = null;
            started = 0;
        }
        return true;
    }

    Card current(long now) {
        if (visible == null || started != 0 && now - started >= visible.holdMs()) {
            visible = waiting.stream().filter(Card::teamFinished).findFirst().orElse(null);
            if (visible == null) visible = waiting.stream().filter(card -> card.completed).findFirst().orElse(null);
            if (visible == null) visible = waiting.stream().filter(Card::unlocked).findFirst().orElse(null);
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
