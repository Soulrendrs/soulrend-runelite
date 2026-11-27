package com.soulrend;

import java.util.ArrayList;
import java.util.List;

/**
 * Conservative one-tick post-clear correlation. No chat transcript leaves
 * this temporary client-thread buffer. Missing or ambiguous pairs stay manual.
 */
final class CombatCompletionWindow {
    private final List<String> lines = new ArrayList<>();
    private int observedTick = -1;
    private boolean overflow;

    void clear() {
        lines.clear();
        observedTick = -1;
        overflow = false;
    }

    void offer(String line, boolean raidNotification, int tick) {
        if (!CombatCompletionParser.candidate(line)) return;
        if (raidNotification && !line.matches("(?i)^(?:Tombs of Amascut|Theatre of Blood).* total completion time: .*")) return;
        if (observedTick != tick) {
            clear();
            observedTick = tick;
        }
        if (lines.size() == 32) overflow = true;
        else if (!overflow) lines.add(line);
    }

    CombatCompletionParser.Completion finish(int tick) {
        if (observedTick < 0) return null;
        var result = !overflow && tick >= observedTick && tick - observedTick <= 1
            ? CombatCompletionParser.parse(lines) : null;
        clear();
        return result;
    }
}
