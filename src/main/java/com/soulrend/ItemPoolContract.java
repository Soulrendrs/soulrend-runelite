package com.soulrend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/** Inactive v2 selector; never a client-side scoring authority. */
final class ItemPoolContract {
    static final int MAX_ITEMS = 128;
    private static final Set<String> FIELDS = Set.of("kind", "strategy", "scope", "target", "items");
    private static final Set<String> LINE_FIELDS = Set.of("item_id", "quantity");
    final String strategy, scope;
    final int target;
    final Map<Integer, Integer> requirements;

    private ItemPoolContract(String strategy, String scope, int target, Map<Integer, Integer> requirements) {
        this.strategy = strategy;
        this.scope = scope;
        this.target = target;
        this.requirements = Map.copyOf(requirements);
    }

    private static String string(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected string");
        return e.getAsString();
    }

    private static int positive(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()
                || !e.getAsString().matches("[1-9][0-9]{0,9}"))
            throw new IllegalArgumentException("Expected positive integer");
        long n = Long.parseLong(e.getAsString());
        if (n > Integer.MAX_VALUE) throw new IllegalArgumentException("Integer overflow");
        return (int) n;
    }

    static ItemPoolContract parse(JsonObject rule, IntUnaryOperator resolveAlias) {
        if (rule == null || !rule.keySet().equals(FIELDS) || !"item_pool".equals(string(rule.get("kind"))))
            throw new IllegalArgumentException("Invalid item pool rule");
        String strategy = string(rule.get("strategy")), scope = string(rule.get("scope"));
        int target = positive(rule.get("target"));
        JsonElement lines = rule.get("items");
        if (!Set.of("sets", "distinct", "quantity").contains(strategy) || !Set.of("team", "solo").contains(scope)
                || !lines.isJsonArray() || lines.getAsJsonArray().size() < 1
                || lines.getAsJsonArray().size() > MAX_ITEMS)
            throw new IllegalArgumentException("Invalid item pool configuration");
        var requirements = new HashMap<Integer, Integer>();
        for (JsonElement line : lines.getAsJsonArray()) {
            if (!line.isJsonObject() || !line.getAsJsonObject().keySet().equals(LINE_FIELDS))
                throw new IllegalArgumentException("Invalid item requirement");
            var item = line.getAsJsonObject();
            int id = positive(item.get("item_id")), quantity = positive(item.get("quantity"));
            if (requirements.containsKey(id) || (!"sets".equals(strategy) && quantity != 1)
                    || resolveAlias.applyAsInt(id) != id)
                throw new IllegalArgumentException("Invalid, duplicate or noncanonical item");
            requirements.put(id, quantity);
        }
        if ("distinct".equals(strategy) && target > requirements.size())
            throw new IllegalArgumentException("Distinct target exceeds item pool");
        return new ItemPoolContract(strategy, scope, target, requirements);
    }

    Map<Integer, Integer> select(Map<Integer, Integer> canonicalCounts, String source) {
        if (!"npc".equals(source) && !"event".equals(source)) return Map.of();
        var result = new HashMap<Integer, Integer>();
        for (int id : requirements.keySet()) {
            int count = canonicalCounts.getOrDefault(id, 0);
            if (count > 0) result.put(id, count);
        }
        return Map.copyOf(result);
    }
}
