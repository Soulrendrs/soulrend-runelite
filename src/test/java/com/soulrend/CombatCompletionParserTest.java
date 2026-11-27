package com.soulrend;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class CombatCompletionParserTest {
    @Test public void preciseBossTimeNotHistoricalPb() {
        var c = CombatCompletionParser.parse(List.of(
            "Your Vorkath kill count is: 100.",
            "Fight duration: 1:36.01. Personal best: 1:20.00"));
        assertNotNull(c);
        assertEquals("vorkath", c.activity);
        assertEquals("normal", c.difficulty);
        assertEquals(9601, c.durationCs);
        assertNotNull(CombatCompletionParser.parse(List.of(
            "Fight duration: 1:36 (new personal best).", "Your Vorkath kill count is: 100.")));
    }

    @Test public void raidTotalAndExactDifficulty() {
        var c = CombatCompletionParser.parse(List.of(
            "Tombs of Amascut: Expert Mode challenge completion time: 30:16. Personal best: 16:08",
            "Tombs of Amascut: Expert Mode total completion time: 34:50 (new personal best)",
            "Your completed Tombs of Amascut: Expert Mode count is: 8."));
        assertNotNull(c);
        assertEquals("toa", c.activity);
        assertEquals("expert", c.difficulty);
        assertEquals(209000, c.durationCs);
        assertNull(CombatCompletionParser.parse(List.of(
            "Tombs of Amascut: Expert Mode total completion time: 34:50",
            "Your completed Tombs of Amascut count is: 8.")));
        c = CombatCompletionParser.parse(List.of(
            "Congratulations - your raid is complete! Duration: 15:39",
            "Your completed Chambers of Xeric Challenge Mode count is: 1."));
        assertNotNull(c);
        assertEquals("challenge", c.difficulty);
        assertEquals(93900, c.durationCs);
    }

    @Test public void gauntletAndTheatre() {
        var c = CombatCompletionParser.parse(List.of(
            "Corrupted challenge duration: 10:24. Personal best: 7:59.",
            "Your Corrupted Gauntlet completion count is: 4."));
        assertNotNull(c);
        assertEquals("corrupted", c.difficulty);
        c = CombatCompletionParser.parse(List.of(
            "Theatre of Blood: Hard Mode total completion time: 1:02:30",
            "Your completed Theatre of Blood: Hard Mode count is: 3."));
        assertNotNull(c);
        assertEquals("hard", c.difficulty);
        assertEquals(375000, c.durationCs);
    }

    @Test public void ambiguousOrIncompleteNeverMatch() {
        assertNull(CombatCompletionParser.parse(List.of(
            "Fight duration: 1:36", "Your Zulrah kill count is: 3.", "Your Vorkath kill count is: 4.")));
        assertNull(CombatCompletionParser.parse(List.of(
            "Fight duration: 1:36", "Fight duration: 1:35", "Your Vorkath kill count is: 4.")));
        assertNull(CombatCompletionParser.parse(List.of(
            "Your completed Tombs of Amascut count is: 8.",
            "Challenge complete: The Wardens. Duration: 7:05")));
        assertNull(CombatCompletionParser.parse(List.of("Fight duration: 1:36")));
        assertNull(CombatCompletionParser.parse(List.of("Your Vorkath kill count is: 4.")));
        assertNull(CombatCompletionParser.parse(List.of(
            "Your completed Fractured Archive count is: 1.",
            "Congratulations - your raid is complete! Duration: 15:39")));
    }

    @Test public void invalidAndPreciseClocks() {
        assertEquals(Integer.valueOf(12345), CombatCompletionParser.centiseconds("2:03.45"));
        assertEquals(Integer.valueOf(12340), CombatCompletionParser.centiseconds("2:03.4"));
        assertNull(CombatCompletionParser.centiseconds("2:60"));
        assertNull(CombatCompletionParser.centiseconds("1:60:00"));
        assertNull(CombatCompletionParser.centiseconds("0:00"));
        assertNull(CombatCompletionParser.centiseconds("1:01.001"));
        assertNull(CombatCompletionParser.centiseconds("25:00:00"));
    }
}
