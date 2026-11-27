package com.soulrend;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import java.util.Set;
import java.util.UUID;

/** Versioned post-clear facts only. No raw messages, party data or PBs. */
final class CombatEvidenceContract {
    private static final Set<String> SUPPORTED = Set.of(
        "vorkath:normal", "zulrah:normal", "gauntlet:normal", "gauntlet:corrupted",
        "cox:normal", "cox:challenge", "tob:normal", "tob:hard", "toa:normal", "toa:expert");

    static boolean supported(String activity, String difficulty) {
        return activity != null && difficulty != null && SUPPORTED.contains(activity + ":" + difficulty);
    }

    static String encode(UUID capture, CombatCompletionParser.Completion completion) {
        if (capture == null || capture.version() != 4 || completion == null
            || !supported(completion.activity, completion.difficulty)
            || completion.counter <= 0 || completion.durationCs <= 0 || completion.durationCs > 8640000)
            throw new IllegalArgumentException("Invalid combat occurrence");
        var fact = new JsonObject();
        fact.addProperty("version", 1);
        fact.addProperty("capture_id", capture.toString());
        fact.addProperty("activity", completion.activity);
        fact.addProperty("difficulty", completion.difficulty);
        fact.addProperty("counter", completion.counter);
        fact.addProperty("duration_cs", completion.durationCs);
        fact.add("own_deaths", JsonNull.INSTANCE);
        fact.add("raid_level", JsonNull.INSTANCE);
        return fact.toString();
    }

    private static boolean integer(JsonElement value, int max) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
            || !value.getAsString().matches("[1-9][0-9]{0,9}")) return false;
        try { return value.getAsLong() <= max; }
        catch (RuntimeException ex) { return false; }
    }

    /** Reject corrupted or mixed saved envelopes before transport. */
    static boolean validPending(PendingSubmissionStore.PendingSubmission proof) {
        if (proof == null || !"combat_clear".equals(proof.evidenceKind) || proof.amount != 1
            || proof.evidenceId != 0 || !"combat".equals(proof.evidenceSource) || proof.evidenceRepeat
            || proof.itemBatch != null || proof.activityEvidence != null || proof.combatFact == null
            || proof.combatFact.length() > 2048 || proof.runeliteCaptureId == null) return false;
        try {
            var id = UUID.fromString(proof.runeliteCaptureId);
            if (id.version() != 4 || !id.toString().equals(proof.runeliteCaptureId)) return false;
            var fact = JsonParser.parseString(proof.combatFact).getAsJsonObject();
            if (!fact.keySet().equals(Set.of("version", "capture_id", "activity", "difficulty",
                "counter", "duration_cs", "own_deaths", "raid_level"))) return false;
            if (!integer(fact.get("version"), 1) || !integer(fact.get("counter"), Integer.MAX_VALUE)
                || !integer(fact.get("duration_cs"), 8640000)
                || !fact.get("own_deaths").isJsonNull() || !fact.get("raid_level").isJsonNull()
                || !fact.get("capture_id").getAsJsonPrimitive().isString()
                || !fact.get("activity").getAsJsonPrimitive().isString()
                || !fact.get("difficulty").getAsJsonPrimitive().isString()
                || !id.toString().equals(fact.get("capture_id").getAsString())) return false;
            return supported(fact.get("activity").getAsString(), fact.get("difficulty").getAsString());
        } catch (RuntimeException ex) { return false; }
    }

    private CombatEvidenceContract() {}
}
