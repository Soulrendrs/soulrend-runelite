package com.soulrend;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Reconciles RuneLite's server NPC loot signal with its inferred client loot signal.
 *
 * ServerNpcLoot is authoritative and can emit once per kill in a same-tick group.
 * NpcLootReceived can be absent, delayed, or represent the same kill. Crucially,
 * having seen one server event in a session does not imply every later NPC emits it.
 *
 * Only observed NPC IDs are carried; neither NPC names nor client-side combat
 * heuristics can manufacture credit for an unrelated monster.
 */
final class NpcKillEvidenceSignals {
    private static final int PAIR_WINDOW_TICKS = 2;

    private static final class Signal {
        final int id;
        final int tick;

        Signal(int id, int tick) {
            this.id = id;
            this.tick = tick;
        }
    }

    private final List<Signal> pendingClients = new ArrayList<>();
    private final List<Signal> unmatchedServers = new ArrayList<>();
    private final List<Signal> creditedClients = new ArrayList<>();

    /**
     * @return whether this server kill still needs credit; false when the exact
     * same NPC was already credited through a delayed client fallback.
     */
    boolean server(int npcId, int tick) {
        if (npcId <= 0) return false;
        if (take(creditedClients, npcId, tick)) return false;
        if (!take(pendingClients, npcId, tick)) {
            unmatchedServers.add(new Signal(npcId, tick));
        }
        return true;
    }

    void client(int npcId, int tick) {
        if (npcId <= 0) return;
        if (!take(unmatchedServers, npcId, tick)) {
            pendingClients.add(new Signal(npcId, tick));
        }
    }

    /** Credit only client loot events for which a server counterpart never arrived. */
    List<Integer> expire(int tick) {
        List<Integer> fallback = new ArrayList<>();
        Iterator<Signal> it = pendingClients.iterator();
        while (it.hasNext()) {
            Signal signal = it.next();
            if (tick < signal.tick) {
                it.remove(); // new game session / reset game clock
            } else if (tick - signal.tick >= PAIR_WINDOW_TICKS) {
                fallback.add(signal.id);
                creditedClients.add(new Signal(signal.id, tick));
                it.remove();
            }
        }
        unmatchedServers.removeIf(signal ->
            tick < signal.tick || tick - signal.tick > PAIR_WINDOW_TICKS);
        creditedClients.removeIf(signal ->
            tick < signal.tick || tick - signal.tick > PAIR_WINDOW_TICKS * 2);
        return fallback;
    }

    void clear() {
        pendingClients.clear();
        unmatchedServers.clear();
        creditedClients.clear();
    }

    private static boolean take(List<Signal> signals, int npcId, int tick) {
        for (int i = 0; i < signals.size(); i++) {
            Signal signal = signals.get(i);
            if (signal.id == npcId && Math.abs((long) tick - signal.tick) <= PAIR_WINDOW_TICKS) {
                signals.remove(i);
                return true;
            }
        }
        return false;
    }
}
