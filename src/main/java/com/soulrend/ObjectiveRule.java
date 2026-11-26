package com.soulrend;

import java.util.Map;
import java.util.Set;

/**
 * Version-one server rule selector. No network, client APIs, or scoring decisions.
 * Extend only with reviewed, fixed observation types; server rules are data, not code.
 */
class ObjectiveRule {
    private static final Set<String> LOOT_SOURCES = Set.of("npc", "event", "pvp", "pickpocket");
    static final Set<String> GATHERING_SKILLS = Set.of("fishing", "mining", "woodcutting", "cooking", "farming", "hunter");

    int tile;
    String kind;
    Integer item_id, quantity, task_id, npc_id;
    int[] npc_ids;
    String source, task_name, npc_name, objective_name, icon_path;
    boolean allow_repeat;

    boolean matchesNpc(int observedNpcId) {
        if (!"npc_kill".equals(kind) || npc_id == null || observedNpcId <= 0) return false;
        if (npc_ids != null && npc_ids.length > 0) {
            for (int allowed : npc_ids) if (allowed == observedNpcId) return true;
            return false;
        }
        return npc_id == observedNpcId;
    }

    boolean matchesDrop(Map<Integer, Integer> items, String actualSource) {
        return "drop".equals(kind) && item_id != null && item_id > 0
            && actualSource != null && LOOT_SOURCES.contains(actualSource)
            && items.getOrDefault(item_id, 0) > 0
            && (source == null || source.isEmpty()
                ? "npc".equals(actualSource) || "event".equals(actualSource)
                : source.equals(actualSource));
    }

    boolean matchesGain(String skill, Map<Integer, Integer> gains) {
        return "gain".equals(kind) && item_id != null && item_id > 0
            && skill != null && GATHERING_SKILLS.contains(skill) && skill.equals(source)
            && gains.getOrDefault(item_id, 0) > 0;
    }

    boolean matchesTask(String task, boolean repeat) {
        return "ca".equals(kind) && task_id != null && task_id >= 0 && task_name != null
            && task != null && !task.isBlank() && task_name.strip().equalsIgnoreCase(task.strip()) && (!repeat || allow_repeat);
    }
}
