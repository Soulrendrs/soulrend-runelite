package com.soulrend;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
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

    private CombatEvidenceContract() {}
}
