package com.soulrend;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemBatchContractTest {
    static final class Vector {
        String label;
        List<int[]> stacks;
        Map<Integer, Integer> aliases;
        Map<Integer, Integer> expected;
    }

    @Test
    public void sharedVectors() throws Exception {
        var gson = new Gson();
        try (var stream = getClass().getResourceAsStream("/runelite_item_batch_v2.json")) {
            assertNotNull(stream);
            List<Vector> vectors = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8),
                new TypeToken<List<Vector>>() {}.getType());
            for (Vector v : vectors) {
                if (v.expected == null) {
                    try {
                        ItemBatchContract.canonicalLoot(v.stacks, id -> v.aliases.getOrDefault(id, id));
                        fail(v.label);
                    } catch (IllegalArgumentException expected) {
                        // Whole batch rejected.
                    }
                } else {
                    assertEquals(v.label, v.expected,
                        ItemBatchContract.canonicalLoot(v.stacks, id -> v.aliases.getOrDefault(id, id)));
                }
            }
        }
    }

    @Test
    public void batchLimitAndMalformedStacks() {
        int limit = ItemBatchContract.MAX_STACKS;
        assertEquals(Map.of(100, limit),
            ItemBatchContract.canonicalLoot(Collections.nCopies(limit, new int[]{100, 1}), id -> id));
        for (List<int[]> stacks : List.of(Collections.nCopies(limit + 1, new int[]{100, 1}),
                List.of(new int[]{100}), List.of(new int[]{100, 1, 2}))) {
            try {
                ItemBatchContract.canonicalLoot(stacks, id -> id);
                fail("Malformed or oversized batch");
            } catch (IllegalArgumentException expected) {
                // Whole batch rejected.
            }
        }
    }
}
