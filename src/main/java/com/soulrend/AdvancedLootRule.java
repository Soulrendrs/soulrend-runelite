package com.soulrend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/** Fixed server rule selection only. The client never computes a GP score. */
final class AdvancedLootRule {
    private AdvancedLootRule() {}

    static Set<Integer> allowedItems(ObjectiveRule rule, IntUnaryOperator resolveAlias) {
        if (rule == null || !rule.isItemBatch()) throw new IllegalArgumentException("Unsupported batch rule");
        if ("item_pool".equals(rule.kind)) {
            var json = new JsonObject();
            json.addProperty("kind", rule.kind);
            json.add("strategy", rule.strategy);
            json.add("scope", rule.scope);
            json.add("target", rule.target);
            json.add("items", rule.items);
            return ItemPoolContract.parse(json, resolveAlias).requirements.keySet();
        }
        if (!isScope(rule.scope) || !positiveLong(rule.target)
                || rule.item_ids == null || !rule.item_ids.isJsonArray()
                || rule.item_ids.getAsJsonArray().size() < 1
                || rule.item_ids.getAsJsonArray().size() > ItemPoolContract.MAX_ITEMS)
            throw new IllegalArgumentException("Invalid loot value rule");
        var ids = new HashSet<Integer>();
        for (JsonElement e : rule.item_ids.getAsJsonArray()) {
            if (!positiveLong(e)) throw new IllegalArgumentException("Invalid item ID");
            long value = Long.parseLong(e.getAsString());
            if (value > Integer.MAX_VALUE || resolveAlias.applyAsInt((int) value) != value
                    || !ids.add((int) value))
                throw new IllegalArgumentException("Unknown or duplicate item");
        }
        return Set.copyOf(ids);
    }

    private static boolean isScope(JsonElement scope) {
        return scope != null && scope.isJsonPrimitive() && scope.getAsJsonPrimitive().isString()
            && Set.of("team", "solo").contains(scope.getAsString());
    }

    private static boolean positiveLong(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()
                || !e.getAsString().matches("[1-9][0-9]{0,18}")) return false;
        try { return Long.parseLong(e.getAsString()) > 0; }
        catch (NumberFormatException ex) { return false; }
    }

    /** Server currently accepts only canonical raw IDs. Never turn a note into
     * a fictional unnoted raw stack to bypass the server's identity check. */
    static JsonObject prepare(UUID captureId, ObjectiveRule rule, String source,
                              Iterable<int[]> rawStacks, IntUnaryOperator resolveAlias) {
        if (!"npc".equals(source) && !"event".equals(source)) return null;
        try {
            var payload = ItemEvidenceBatch.prepare(captureId, allowedItems(rule, resolveAlias),
                source, rawStacks, resolveAlias);
            if (payload == null) return null;
            for (JsonElement row : payload.getAsJsonArray("stacks")) {
                int id = row.getAsJsonArray().get(0).getAsInt();
                if (resolveAlias.applyAsInt(id) != id) return null;
            }
            return payload;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
