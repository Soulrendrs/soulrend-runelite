package com.soulrend;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BingoObjectiveArtworkPathTest {
    @Test
    public void onlyAllowsSameOriginObjectiveArtworkPaths() {
        assertTrue(BingoApiClient.isBingoIconPath("/runescape-assets/npc-icon/655/"));
        assertTrue(BingoApiClient.isBingoIconPath("/static/runelite-items/526.png"));
        assertFalse(BingoApiClient.isBingoIconPath("//other.example/runescape-assets/npc-icon/655/"));
        assertFalse(BingoApiClient.isBingoIconPath("/runescape-assets/npc-icon/655/../"));
        assertFalse(BingoApiClient.isBingoIconPath("/runescape-assets/npc-icon/655/?url=example"));
        assertFalse(BingoApiClient.isBingoIconPath(null));
    }
}
