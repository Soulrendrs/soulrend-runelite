package com.soulrend;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict post-completion parser. No live timers, PB sync, opponent tracking,
 * or arbitrary server-supplied patterns. Not connected to evidence upload.
 * Caller must supply a bounded, same-session batch of trusted game messages
 * from one completion window; player/public/clan chat is never accepted.
 */
final class CombatCompletionParser {
    private static final Pattern COUNT = Pattern.compile(
        "^Your (.+?) (?:kill|completion) count is: [0-9,]+\\.?$");
    private static final Pattern RAID_COUNT = Pattern.compile(
        "^Your completed (.+?) count is: [0-9,]+\\.?$");
    private static final Pattern CLOCK = Pattern.compile(
        "(?:([0-9]{1,2}):)?([0-9]{1,3}):([0-9]{2})(?:\\.([0-9]{1,2}))?");
    private static final Pattern TIME = Pattern.compile(
        "^(Fight duration|Challenge duration|Corrupted challenge duration|"
        + "Duration|Chambers of Xeric: .*?Duration|"
        + "Congratulations, your raid is complete! Duration|"
        + "Tombs of Amascut(?:: (?:Expert|Normal) Mode)? total completion time|"
        + "Theatre of Blood(?:: Hard Mode)? total completion time): "
        + "([0-9:.]+)(?: \\(new personal best\\)|\\.?(?: Personal best: [0-9:.]+)?\\.?)$",
        Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> ACTIVITIES = Map.ofEntries(
        Map.entry("vorkath", "vorkath:normal"),
        Map.entry("zulrah", "zulrah:normal"),
        Map.entry("gauntlet", "gauntlet:normal"),
        Map.entry("corrupted gauntlet", "gauntlet:corrupted"),
        Map.entry("chambers of xeric", "cox:normal"),
        Map.entry("chambers of xeric challenge mode", "cox:challenge"),
        Map.entry("theatre of blood", "tob:normal"),
        Map.entry("theatre of blood hard mode", "tob:hard"),
        Map.entry("tombs of amascut", "toa:normal"),
        Map.entry("tombs of amascut expert mode", "toa:expert"));

    static final class Completion {
        final String activity;
        final String difficulty;
        final int durationCs;
        Completion(String key, int durationCs) {
            String[] parts = key.split(":");
            this.activity = parts[0];
            this.difficulty = parts[1];
            this.durationCs = durationCs;
        }
    }

    static Integer centiseconds(String clock) {
        Matcher m = CLOCK.matcher(clock);
        if (!m.matches()) return null;
        int hours = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
        int minutes = Integer.parseInt(m.group(2));
        int seconds = Integer.parseInt(m.group(3));
        if (seconds >= 60 || m.group(1) != null && minutes >= 60) return null;
        String fraction = m.group(4);
        int cs = fraction == null ? 0 : Integer.parseInt(fraction) * (fraction.length() == 1 ? 10 : 1);
        int total = ((hours * 60 + minutes) * 60 + seconds) * 100 + cs;
        return total > 0 && total <= 8640000 ? total : null;
    }

    private static String activity(String line) {
        Matcher count = COUNT.matcher(line);
        if (!count.matches()) {
            count = RAID_COUNT.matcher(line);
            if (!count.matches()) return null;
        }
        String name = TimedClearParser.norm(count.group(1));
        return ACTIVITIES.get(name);
    }

    private static boolean compatible(String key, String label) {
        String name = label.toLowerCase(Locale.ROOT);
        switch (key) {
            case "vorkath:normal":
            case "zulrah:normal": return name.equals("fight duration");
            case "gauntlet:normal": return name.equals("challenge duration");
            case "gauntlet:corrupted": return name.equals("corrupted challenge duration");
            case "cox:normal":
            case "cox:challenge":
                return name.equals("congratulations, your raid is complete! duration");
            case "tob:normal": return name.equals("theatre of blood total completion time");
            case "tob:hard": return name.equals("theatre of blood: hard mode total completion time");
            case "toa:normal": return name.equals("tombs of amascut total completion time")
                || name.equals("tombs of amascut: normal mode total completion time");
            case "toa:expert": return name.equals("tombs of amascut: expert mode total completion time");
            default: return false;
        }
    }

    /**
     * Exactly one recognized count line and one compatible final duration.
     * Multiple count signals, including unknown activities, invalidate the
     * batch; a historical PB, room time or incomplete batch never qualifies.
     */
    static Completion parse(List<String> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > 32) return null;
        String key = null;
        String label = null;
        Integer time = null;
        for (String line : lines) {
            if (line == null || line.length() > 512) return null;
            if (COUNT.matcher(line).matches() || RAID_COUNT.matcher(line).matches()) {
                if (key != null) return null;
                key = activity(line);
                if (key == null) return null;
            }
            Matcher m = TIME.matcher(line);
            if (m.matches()) {
                if (time != null) return null;
                label = m.group(1);
                // A final punctuation dot is not a fractional digit.
                String raw = m.group(2);
                if (raw.endsWith(".")) raw = raw.substring(0, raw.length() - 1);
                time = centiseconds(raw);
                if (time == null) return null;
            }
        }
        return key != null && time != null && compatible(key, label)
            ? new Completion(key, time) : null;
    }

    private CombatCompletionParser() {}
}
