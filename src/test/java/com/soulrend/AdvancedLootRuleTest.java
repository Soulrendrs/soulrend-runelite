package com.soulrend;

import com.google.gson.Gson;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdvancedLootRuleTest {
    private static final Gson GSON = new Gson();
    private static final UUID CAPTURE = UUID.fromString("123e4567-e89b-42d3-a456-426614174000");
    private static int resolve(int id) { return id == 101 ? 100 : id; }

    private ObjectiveRule rule(String json) { return GSON.fromJson(json, ObjectiveRule.class); }

    @Test public void poolSelectsOnlyRelevantRawItemsAndKeepsStableCapture() {
        var rule = rule("{\"kind\":\"item_pool\",\"strategy\":\"sets\",\"scope\":\"team\",\"target\":2,"
            + "\"items\":[{\"item_id\":100,\"quantity\":2},{\"item_id\":200,\"quantity\":1}]}");
        var raw = List.of(new int[]{999, 20}, new int[]{200, 1}, new int[]{100, 4});
        var payload = AdvancedLootRule.prepare(CAPTURE, rule, "event", raw, AdvancedLootRuleTest::resolve);
        assertNotNull(payload);
        assertEquals(CAPTURE.toString(), payload.get("capture_id").getAsString());
        assertEquals("[[100,4],[200,1]]", payload.get("stacks").toString());
        assertEquals(Set.of("capture_id", "source", "stacks"), payload.keySet());
        assertNull(AdvancedLootRule.prepare(CAPTURE, rule, "pvp", raw, AdvancedLootRuleTest::resolve));
        assertNull(AdvancedLootRule.prepare(CAPTURE, rule, "pickpocket", raw, AdvancedLootRuleTest::resolve));
        assertNull(AdvancedLootRule.prepare(CAPTURE, rule, "npc",
            List.of(new int[]{101, 3}, new int[]{200, 1}), AdvancedLootRuleTest::resolve));
    }

    @Test public void valueRuleSupportsLongTargetButNoClientPrice() {
        var rule = rule("{\"kind\":\"loot_value\",\"scope\":\"solo\",\"target\":10000000000,\"item_ids\":[100,200]}");
        assertEquals(Set.of(100, 200), AdvancedLootRule.allowedItems(rule, AdvancedLootRuleTest::resolve));
        var payload = AdvancedLootRule.prepare(CAPTURE, rule, "npc", List.of(new int[]{100, 2}), AdvancedLootRuleTest::resolve);
        assertNotNull(payload);
        assertFalse(payload.has("value"));
        assertFalse(payload.has("price"));
        for (String invalid : List.of("9223372036854775808", "0", "true", "\"2\"")) {
            var bad = rule("{\"kind\":\"loot_value\",\"scope\":\"team\",\"target\":" + invalid + ",\"item_ids\":[100]}");
            assertNull(AdvancedLootRule.prepare(CAPTURE, bad, "npc", List.of(new int[]{100, 2}), AdvancedLootRuleTest::resolve));
        }
    }

    @Test public void queuedBatchRoundTripsAndUsesFixedMultipartEnvelope() {
        var rule = rule("{\"kind\":\"loot_value\",\"scope\":\"team\",\"target\":500,\"item_ids\":[100]}");
        var proof = new PendingSubmissionStore.PendingSubmission();
        proof.tileId = 7;
        proof.runeliteCaptureId = CAPTURE.toString();
        proof.timestamp = 1700000000000L;
        proof.evidenceKind = "loot_value";
        proof.evidenceSource = "event";
        proof.itemBatch = AdvancedLootRule.prepare(CAPTURE, rule, "event",
            List.of(new int[]{100, 2}), AdvancedLootRuleTest::resolve);
        var restored = GSON.fromJson(GSON.toJson(proof), PendingSubmissionStore.PendingSubmission.class);
        assertEquals(proof.itemBatch, restored.itemBatch);
        var body = BingoApiClient.evidenceBody(restored, new byte[]{1, 2, 3});
        var names = new java.util.HashSet<String>();
        for (var part : body.parts()) names.add(part.headers().get("Content-Disposition").split("\"")[1]);
        assertEquals(Set.of("tile", "capture_id", "captured_at", "source", "item_batch", "image"), names);
        restored.itemBatch = null;
        restored.evidenceKind = "drop";
        var legacy = BingoApiClient.evidenceBody(restored, new byte[]{1, 2, 3});
        names.clear();
        for (var part : legacy.parts()) names.add(part.headers().get("Content-Disposition").split("\"")[1]);
        assertEquals(Set.of("tile", "capture_id", "captured_at", "kind", "objective_id", "source", "quantity", "image"), names);
        var receipt = new BingoApiClient.EvidenceUpload(200, "tracking", 5_000_000_000L, 10_000_000_000L, null, null, 1);
        assertEquals(Long.valueOf(5_000_000_000L), receipt.value);
    }
}
