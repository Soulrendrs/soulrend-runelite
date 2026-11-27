package com.soulrend;

import java.util.UUID;

/**
 * One observed local-player run, owned by the future game-event adapter.
 * No party identifiers, persistence, combat hints, or network access.
 * Only trusted observed boundaries can open or finish a run; a caller must
 * invalidate on logout, world/session/character change and uncertain start.
 */
final class CombatRunTracker {
    static final class Fact {
        final UUID captureId;
        final String activity;
        final String difficulty;
        final int durationCs;
        final Integer ownDeaths;
        final Integer raidLevel;

        Fact(UUID captureId, CombatCompletionParser.Completion completion,
             Integer ownDeaths, Integer raidLevel) {
            this.captureId = captureId;
            this.activity = completion.activity;
            this.difficulty = completion.difficulty;
            this.durationCs = completion.durationCs;
            this.ownDeaths = ownDeaths;
            this.raidLevel = raidLevel;
        }
    }

    private String activity;
    private String difficulty;
    private String session;
    private int deaths;

    void invalidate() {
        activity = null;
        difficulty = null;
        session = null;
        deaths = 0;
    }

    /** Called only from a separately verified start signal, not a login/region guess. */
    void start(String session, String activity, String difficulty) {
        invalidate();
        if (session == null || session.isEmpty() || activity == null || difficulty == null) return;
        this.session = session;
        this.activity = activity;
        this.difficulty = difficulty;
    }

    void localDeath(String currentSession) {
        if (session == null || !session.equals(currentSession)) {
            invalidate();
        } else if (deaths == Integer.MAX_VALUE) {
            invalidate();
        } else {
            deaths++;
        }
    }

    /**
     * Consume the tracked run. Unknown start/lifetime becomes null, never zero.
     * Level is an optional separately verified FINAL chest observation; do not
     * pass a lobby level or unverified varbit. Capture identity is supplied by
     * the existing evidence queue and must be reused for matching tiles/retries.
     */
    Fact finish(String currentSession, UUID captureId,
                CombatCompletionParser.Completion completion, Integer finalRaidLevel) {
        if (completion == null || currentSession == null || currentSession.isEmpty()
            || captureId == null || captureId.version() != 4) {
            invalidate();
            return null;
        }
        Integer ownDeaths = session != null && session.equals(currentSession)
            && activity.equals(completion.activity) && difficulty.equals(completion.difficulty)
            ? deaths : null;
        invalidate();
        Integer level = "toa".equals(completion.activity) && finalRaidLevel != null
            && finalRaidLevel >= 0 && finalRaidLevel <= 1000 ? finalRaidLevel : null;
        return new Fact(captureId, completion, ownDeaths, level);
    }
}
