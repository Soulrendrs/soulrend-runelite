package com.soulrend;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Inactive candidate parser. No client access, network, chat relay or score award. */
final class ActivityObjectiveSignals {
    static final Map<String, Integer> IDS = Map.of(
        "sepulchre_floor_1", 1, "sepulchre_floor_2", 2, "sepulchre_floor_3", 3,
        "sepulchre_floor_4", 4, "sepulchre_floor_5", 5,
        "grand_hallowed_coffin", 6, "hunter_rumour", 7);

    private static final String COUNT = "([1-9][0-9]{0,9}|[1-9][0-9]{0,2}(?:,[0-9]{3}){1,3})";
    private static final Pattern FLOOR = Pattern.compile(
        "You have completed Floor ([1-5]) of the Hallowed Sepulchre! Total completions: " + COUNT + "\\.");
    private static final Pattern COFFIN = Pattern.compile(
        "You have opened the Grand Hallowed Coffin " + COUNT + " times?!");
    private static final Pattern RUMOUR = Pattern.compile(
        "You have completed " + COUNT + " rumours? for the Hunter Guild\\.");

    static final class Signal {
        final String activity;
        final int count;
        Signal(String activity, int count) {
            this.activity = activity;
            this.count = count;
        }
    }

    /** Accept normalized, own-game message text only; caller must verify event type.
     * Do not wire to live chat until historical query and game-mode behavior are verified. */
    static Signal parse(String message) {
        if (message == null || message.length() > 200) return null;
        var floor = FLOOR.matcher(message);
        if (floor.matches()) return signal("sepulchre_floor_" + floor.group(1), floor.group(2));
        var coffin = COFFIN.matcher(message);
        if (coffin.matches()) return signal("grand_hallowed_coffin", coffin.group(1));
        var rumour = RUMOUR.matcher(message);
        if (rumour.matches()) return signal("hunter_rumour", rumour.group(1));
        return null;
    }

    private static Signal signal(String activity, String count) {
        try {
            int n = Integer.parseInt(count.replace(",", ""));
            return n > 0 ? new Signal(activity, n) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** One baseline per activity within a single uninterrupted eligible epoch.
     * A new session/world/account/rule epoch MUST reset this before reading. */
    static final class ConsecutiveCounter {
        private final Map<String, Integer> highWater = new HashMap<>();

        boolean observe(Signal signal) {
            if (signal == null || signal.activity == null || !IDS.containsKey(signal.activity)
                || signal.count <= 0) return false;
            Integer previous = highWater.get(signal.activity);
            if (previous != null && signal.count <= previous) return false;
            highWater.put(signal.activity, signal.count);
            return previous != null && (long) signal.count - previous == 1;
        }

        void reset() { highWater.clear(); }
    }

    private ActivityObjectiveSignals() {}
}
