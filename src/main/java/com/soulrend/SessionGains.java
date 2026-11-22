package com.soulrend;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.Locale.ROOT;

/** Session observations for display only. First readings are baselines, even for boss KC. */
final class SessionGains {
    private static final Set<String> BOSSES = Set.of("araxxor", "vorkath", "zulrah");
    private final Map<String, Integer> baselines = new HashMap<>();
    private final Map<String, Long> gains = new HashMap<>();
    private String character;
    private String sessionToken;

    synchronized void skill(String rsn, String token, String skill, int xp) {
        String metric = skill.toLowerCase(ROOT);
        observe(rsn, token, "skill_xp:" + ("runecraft".equals(metric) ? "runecrafting" : metric), xp);
    }

    synchronized void boss(String rsn, String token, String boss, int kc) {
        String metric = boss.toLowerCase(ROOT);
        if (BOSSES.contains(metric)) observe(rsn, token, "boss_kc:" + metric, kc);
    }

    private void observe(String rsn, String token, String key, int total) {
        if (rsn == null || rsn.isBlank() || token == null || token.isBlank() || total < 0) return;
        if (!Objects.equals(token, sessionToken) || character == null || !Rsn.same(character, rsn)) {
            reset();
            character = rsn;
            sessionToken = token;
        }
        Integer previous = baselines.get(key);
        if (previous == null || total > previous) baselines.put(key, total);
        if (previous != null && total > previous) gains.merge(key, (long) total - previous, Long::sum);
    }

    synchronized Map<String, Long> snapshot(String rsn, String token) {
        return character != null && rsn != null && Rsn.same(character, rsn) && Objects.equals(token, sessionToken)
            ? Map.copyOf(gains) : Map.of();
    }

    synchronized void reset() {
        character = null;
        sessionToken = null;
        baselines.clear();
        gains.clear();
    }
}
