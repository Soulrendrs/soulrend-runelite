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
    public void matchesPunctuatedBossesAndKeepsRaidVariantsSeparate() {
        SessionGains gains = new SessionGains();
        gains.boss("Player", "account", "Barrows", 10);
        gains.boss("Player", "account", "Barrows Chests", 11);
        gains.boss("Player", "account", "K'ril Tsutsaroth", 20);
        gains.boss("Player", "account", "K'ril Tsutsaroth", 21);
        gains.boss("Player", "account", "Tombs of Amascut", 4);
        gains.boss("Player", "account", "Tombs of Amascut (Expert Mode)", 9);
        gains.boss("Player", "account", "Tombs of Amascut (Expert Mode)", 10);
        gains.skill("Player", "account", "Runecraft", 100);
        gains.skill("Player", "account", "Runecraft", 105);
        gains.skill("Player", "account", "Sailing", 9);
        gains.skill("Player", "account", "Sailing", 11);
        assertEquals(Map.of("boss_kc:barrows_chests", 1L, "boss_kc:kril_tsutsaroth", 1L,
            "boss_kc:tombs_of_amascut_expert", 1L, "skill_xp:runecrafting", 5L, "skill_xp:sailing", 2L),
            gains.snapshot("Player", "account"));
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
