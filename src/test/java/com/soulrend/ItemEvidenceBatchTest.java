package com.soulrend;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemEvidenceBatchTest {
    static final class Fixture {
        String capture_id;
        JsonObject rule;
        Map<Integer, Integer> aliases;
        List<Vector> cases;
    }
    static final class Vector {
        String label, source;
        List<int[]> stacks;
        JsonElement payload;
        boolean invalid;
    }

    @Test
    public void sharedPayloads() throws Exception {
        try (var stream = getClass().getResourceAsStream("/runelite_item_evidence_v2.json")) {
            assertNotNull(stream);
            Fixture fixture = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Fixture.class);
            var resolver = (java.util.function.IntUnaryOperator) id -> fixture.aliases.getOrDefault(id, 0);
            var pool = ItemPoolContract.parse(fixture.rule, resolver);
            var id = UUID.fromString(fixture.capture_id);
            for (Vector v : fixture.cases) {
                if (v.invalid) {
                    try {
                        ItemEvidenceBatch.prepare(id, pool, v.source, v.stacks, resolver);
                        fail(v.label);
                    } catch (IllegalArgumentException expected) {
                        // Whole batch rejected.
                    }
                } else {
                    var actual = ItemEvidenceBatch.prepare(id, pool, v.source, v.stacks, resolver);
                    if (v.payload == null || v.payload.isJsonNull()) assertNull(v.label, actual);
                    else assertEquals(v.label, v.payload, actual);
                }
            }
            var raw = new int[]{101, 3};
            var prepared = ItemEvidenceBatch.prepare(id, pool, "npc", List.of(raw), resolver);
            raw[1] = 9;
            assertEquals(3, prepared.getAsJsonArray("stacks").get(0).getAsJsonArray().get(1).getAsInt());
            try {
                ItemEvidenceBatch.prepare(id, pool, "npc",
                    Collections.nCopies(ItemBatchContract.MAX_STACKS + 1, new int[]{100, 1}), resolver);
                fail("Oversized batch");
            } catch (IllegalArgumentException expected) {
                // Rejected.
            }
        }
    }
}
