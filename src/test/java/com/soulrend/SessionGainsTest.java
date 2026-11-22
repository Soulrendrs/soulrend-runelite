package com.soulrend;

import org.junit.Test;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SessionGainsTest {
    @Test
    public void duplicateAndStaleReadingsCannotInflateTheSession() {
        SessionGains gains = new SessionGains();
        for (int xp : new int[] {120, 120, 112, 130, 130, 132}) gains.skill("Player", "account", "Mining", xp);
        for (int kc : new int[] {500, 500, 499, 501}) gains.boss("Player", "account", "Zulrah", kc);
        gains.boss("Player", "account", "Unverified Boss", 99);

        assertEquals(Map.of("skill_xp:mining", 12L, "boss_kc:zulrah", 1L),
            gains.snapshot("player", "account"));
    }

    @Test
    public void excludesOtherCharactersAndWebsiteSessions() {
        SessionGains gains = new SessionGains();
        gains.skill("First", "account", "Mining", 100);
        gains.skill("First", "account", "Mining", 110);

        assertTrue(gains.snapshot("Second", "account").isEmpty());
        assertTrue(gains.snapshot("First", "other-account").isEmpty());
        gains.skill("First", "other-account", "Mining", 200);
        assertTrue(gains.snapshot("First", "account").isEmpty());
        assertTrue(gains.snapshot("First", "other-account").isEmpty());

        gains.skill("First", "other-account", "Mining", 209);
        gains.reset();
        assertTrue(gains.snapshot("First", "other-account").isEmpty());
    }
}
