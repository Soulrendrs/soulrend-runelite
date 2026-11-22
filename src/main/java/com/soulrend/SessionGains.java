package com.soulrend;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static java.util.Locale.ROOT;

/** Session observations for display only. First readings are baselines, even for boss KC. */
final class SessionGains {
    // Includes skills and recognized KC lines for activities that may not have a board metric.
    private static final int MAX_METRICS = 256;
    private static final Pattern BOSS_SEPARATOR = Pattern.compile("[^a-z0-9]+");
    private static final Map<String, String> BOSS_ALIASES = Map.ofEntries(
        Map.entry("barrows", "barrows_chests"),
        Map.entry("lunar_chest", "lunar_chests"),
        Map.entry("k_ril_tsutsaroth", "kril_tsutsaroth"),
        Map.entry("kree_arra", "kreearra"),
        Map.entry("vet_ion", "vetion"),
        Map.entry("calvar_ion", "calvarion"),
        Map.entry("phosani_s_nightmare", "phosanis_nightmare"),
        Map.entry("inferno", "tzkal_zuk"),
        Map.entry("fight_caves", "tztok_jad"),
        Map.entry("gauntlet", "the_gauntlet"),
        Map.entry("corrupted_gauntlet", "the_corrupted_gauntlet"),
        Map.entry("chambers_of_xeric_cm", "chambers_of_xeric_challenge_mode"),
        Map.entry("theatre_of_blood_hm", "theatre_of_blood_hard_mode"),
        Map.entry("tombs_of_amascut_expert_mode", "tombs_of_amascut_expert")
    );
    private final Map<String, Integer> baselines = new HashMap<>();
    private final Map<String, Long> gains = new HashMap<>();
    private String character;
    private String sessionToken;

    synchronized void skill(String rsn, String token, String skill, int xp) {
        String metric = skill.toLowerCase(ROOT);
        observe(rsn, token, "skill_xp:" + ("runecraft".equals(metric) ? "runecrafting" : metric), xp);
    }

    synchronized void boss(String rsn, String token, String boss, int kc) {
        if (boss == null || boss.length() > 128) return;
        String metric = BOSS_SEPARATOR.matcher(boss.strip().toLowerCase(ROOT)).replaceAll("_");
        if (metric.endsWith("_")) metric = metric.substring(0, metric.length() - 1);
        if (!metric.isBlank()) observe(rsn, token, "boss_kc:" + BOSS_ALIASES.getOrDefault(metric, metric), kc);
    }

    private void observe(String rsn, String token, String key, int total) {
        if (rsn == null || rsn.isBlank() || token == null || token.isBlank() || total < 0) return;
        if (!Objects.equals(token, sessionToken) || character == null || !Rsn.same(character, rsn)) {
            reset();
            character = rsn;
            sessionToken = token;
        }
        Integer previous = baselines.get(key);
        if (previous == null && baselines.size() >= MAX_METRICS) return;
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
