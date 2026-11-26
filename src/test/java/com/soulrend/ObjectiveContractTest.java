package com.soulrend;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ObjectiveContractTest {
    @Test
    public void sharedContractVectors() throws Exception {
        var gson = new Gson();
        try (var stream = getClass().getResourceAsStream("/runelite_objective_contract_v1.json")) {
            assertNotNull(stream);
            var cases = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonArray.class);
            for (var entry : cases) {
                var c = entry.getAsJsonObject();
                // Exercise the existing Gson wire subtype, including inherited fields.
                var rule = gson.fromJson(c.get("rule"), AutomaticEvidence.Rule.class);
                int id = c.get("id").getAsInt();
                int quantity = c.get("quantity").getAsInt();
                String source = c.get("source").getAsString();
                boolean accepted;
                switch (c.get("kind").getAsString()) {
                    case "drop":
                        accepted = rule.matchesDrop(Map.of(id, quantity), source);
                        break;
                    case "gain":
                        accepted = rule.matchesGain(source, Map.of(id, quantity));
                        break;
                    case "npc_kill":
                        accepted = quantity == 1 && source.isEmpty() && rule.matchesNpc(id);
                        break;
                    case "ca":
                        accepted = rule.task_id != null && id == rule.task_id
                            && rule.matchesTask(c.get("task").getAsString(), c.get("repeat").getAsBoolean());
                        break;
                    default:
                        accepted = false;
                }
                assertEquals(c.get("label").getAsString(), c.get("accepted").getAsBoolean(), accepted);
            }
        }
    }
}
