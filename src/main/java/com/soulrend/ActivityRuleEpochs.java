package com.soulrend;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Client-thread-only, per eligible tile and activity baseline. No raw chat storage. */
final class ActivityRuleEpochs {
    static final class Entry {
        final int event, tile;
        final String epoch;
        Entry(int event, int tile, String epoch) {
            this.event = event;
            this.tile = tile;
            this.epoch = epoch;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Entry)) return false;
            var e = (Entry) other;
            return event == e.event && tile == e.tile && Objects.equals(epoch, e.epoch);
        }
        @Override public int hashCode() { return Objects.hash(event, tile, epoch); }
    }

    private String session, character;
    private final Map<String, Map<Entry, ActivityObjectiveSignals.ConsecutiveCounter>> counters = new HashMap<>();

    Set<Entry> observe(String session, String character, ActivityObjectiveSignals.Signal signal, List<Entry> entries) {
        if (!Objects.equals(this.session, session) || !Objects.equals(this.character, character)) reset();
        this.session = session;
        this.character = character;
        if (session == null || character == null || signal == null || signal.activity == null
            || !ActivityObjectiveSignals.IDS.containsKey(signal.activity) || entries.size() > 512) {
            reset();
            return Set.of();
        }
        var perActivity = counters.computeIfAbsent(signal.activity, k -> new HashMap<>());
        var allowed = new HashSet<>(entries);
        perActivity.keySet().retainAll(allowed);
        var accepted = new HashSet<Entry>();
        for (var entry : allowed) {
            if (entry == null || entry.event <= 0 || entry.tile <= 0 || entry.epoch == null
                || !entry.epoch.matches("[A-Za-z0-9_-]{1,80}")) continue;
            if (perActivity.computeIfAbsent(entry, k -> new ActivityObjectiveSignals.ConsecutiveCounter()).observe(signal))
                accepted.add(entry);
        }
        return accepted;
    }

    void reset() {
        counters.clear();
        session = character = null;
    }
}
