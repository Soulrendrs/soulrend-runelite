package com.soulrend;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemPoolContractTest {
    static final class Vector {
        String label, source;
        JsonObject rule;
        boolean valid;
        Map<Integer, Integer> counts, matched;
    }

    @Test
    public void sharedVectors() throws Exception {
        try (var stream = getClass().getResourceAsStream("/runelite_item_pool_v2.json")) {
            assertNotNull(stream);
            List<Vector> cases = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8),
                new TypeToken<List<Vector>>() {}.getType());
            for (Vector v : cases) {
                if (!v.valid) {
                    try {
                        ItemPoolContract.parse(v.rule, id -> Map.of(100, 100, 101, 100, 200, 200).getOrDefault(id, 0));
                        fail(v.label);
                    } catch (IllegalArgumentException expected) {
                        // Invalid rules never match.
                    }
                } else {
                    var rule = ItemPoolContract.parse(v.rule, id -> Map.of(100, 100, 101, 100, 200, 200).getOrDefault(id, 0));
                    assertEquals(v.label, v.matched, rule.select(v.counts, v.source));
                }
            }
        }
    }

    @Test
    public void poolSizeIsBounded() {
        var rule = new JsonObject();
        rule.addProperty("kind", "item_pool");
        rule.addProperty("strategy", "sets");
        rule.addProperty("scope", "team");
        rule.addProperty("target", 1);
        var items = new com.google.gson.JsonArray();
        rule.add("items", items);
        for (int i = 1; i <= ItemPoolContract.MAX_ITEMS + 1; i++) {
            var line = new JsonObject();
            line.addProperty("item_id", i);
            line.addProperty("quantity", 1);
            items.add(line);
            if (i == ItemPoolContract.MAX_ITEMS)
                assertEquals(i, ItemPoolContract.parse(rule, id -> id).requirements.size());
        }
        try {
            ItemPoolContract.parse(rule, id -> id);
            fail("Oversized pool");
        } catch (IllegalArgumentException expected) {
            // Rejected.
        }
    }
}
