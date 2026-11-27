package com.soulrend;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Per eligible tile and activity baseline; rule refresh and client observations share a lock. */
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
    private long generation;

    synchronized long generation() { return generation; }

    synchronized boolean isCurrent(long snapshotGeneration) { return generation == snapshotGeneration; }

    /** Loading, hop, logout or consent reset invalidates in-flight rule refreshes. */
    synchronized void invalidate() {
        generation++;
        reset();
    }
    private final Map<String, Map<Entry, ActivityObjectiveSignals.ConsecutiveCounter>> counters = new HashMap<>();

    synchronized Set<Entry> observe(String session, String character, ActivityObjectiveSignals.Signal signal, List<Entry> entries) {
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
            if (entry == null || entry.event <= 0 || entry.tile <= 0 || !ActivityEvidenceContract.validEpoch(entry.epoch)) continue;
            if (perActivity.computeIfAbsent(entry, k -> new ActivityObjectiveSignals.ConsecutiveCounter()).observe(signal))
                accepted.add(entry);
        }
        return accepted;
    }

    /** A successful rule refresh must remove absent entries even if no signal arrived. */
    synchronized void retain(String session, String character, Set<Entry> allowed) {
        if (!Objects.equals(this.session, session) || !Objects.equals(this.character, character)) {
            reset();
            return;
        }
        for (var perActivity : counters.values()) perActivity.keySet().retainAll(allowed);
        counters.values().removeIf(Map::isEmpty);
    }

    synchronized void reset() {
        counters.clear();
        session = character = null;
    }
}
