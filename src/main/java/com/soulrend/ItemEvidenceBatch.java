package com.soulrend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/** Inactive v2 batch preparation. Call only after consent and local rule discovery. */
final class ItemEvidenceBatch {
    private ItemEvidenceBatch() {}

    /**
     * One occurrence UUID is supplied by the caller and reused across matched tiles
     * and retries. Only relevant raw stacks are serialized, never canonical totals,
     * prices, unrelated inventory, player identity or executable rule data.
     */
    static JsonObject prepare(UUID captureId, ItemPoolContract pool, String source,
                              Iterable<int[]> rawStacks, IntUnaryOperator resolveAlias) {
        return prepare(captureId, pool.requirements.keySet(), source, rawStacks, resolveAlias);
    }

    static JsonObject prepare(UUID captureId, Set<Integer> allowed, String source,
                              Iterable<int[]> rawStacks, IntUnaryOperator resolveAlias) {
        if (!"npc".equals(source) && !"event".equals(source)) return null;
        if (captureId == null || captureId.version() != 4 || captureId.variant() != 2)
            throw new IllegalArgumentException("Expected UUIDv4");
        var snapshot = new ArrayList<int[]>();
        for (int[] stack : rawStacks) {
            if (snapshot.size() >= ItemBatchContract.MAX_STACKS)
                throw new IllegalArgumentException("Loot batch exceeds stack limit");
            if (stack == null || stack.length != 2)
                throw new IllegalArgumentException("Invalid loot stack");
            snapshot.add(stack.clone());
        }
        var canonical = ItemBatchContract.canonicalLoot(snapshot, resolveAlias);
        if (canonical.keySet().stream().noneMatch(allowed::contains)) return null;
        List<int[]> relevant = new ArrayList<>();
        for (int[] stack : snapshot) {
            if (allowed.contains(resolveAlias.applyAsInt(stack[0]))) relevant.add(stack);
        }
        relevant.sort(Comparator.<int[]>comparingInt(s -> s[0]).thenComparingInt(s -> s[1]));
        var lines = new JsonArray();
        for (int[] stack : relevant) {
            var line = new JsonArray();
            line.add(stack[0]);
            line.add(stack[1]);
            lines.add(line);
        }
        var payload = new JsonObject();
        payload.addProperty("capture_id", captureId.toString());
        payload.addProperty("source", source);
        payload.add("stacks", lines);
        return payload;
    }
}
