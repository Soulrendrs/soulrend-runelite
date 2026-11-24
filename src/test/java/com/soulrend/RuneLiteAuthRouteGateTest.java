package com.soulrend;

import okhttp3.HttpUrl;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Phase 2A must never send inherited Anvil event/proof paths to production. */
public class RuneLiteAuthRouteGateTest
{
    @Test
    public void firstPartyDeviceAndSessionRoutesAreAllowed()
    {
        assertTrue(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/api/runelite/v1/device/start")));
        assertTrue(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/api/runelite/v1/device/poll")));
        assertTrue(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/api/runelite/v1/session/revoke")));
        assertTrue(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/api/runelite/v1/me")));
    }

    @Test
    public void inheritedUnimplementedProductionRoutesAreBlocked()
    {
        assertFalse(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/api/plugin/config")));
        assertFalse(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/api/plugin/auth/start")));
        assertFalse(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/api/events/3/submissions")));
        assertFalse(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/c/clan/api/upload")));
        assertFalse(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/api/plugin/push/ticket")));
    }

    @Test
    public void onlyExactPublicObjectiveArtworkRoutesAreAllowed()
    {
        assertTrue(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/runescape-assets/npc-icon/655/")));
        assertTrue(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/static/runelite-items/526.png")));
        assertTrue(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/static/wom-metrics/attack.png")));
        assertFalse(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/runescape-assets/npc-icon/655/extra")));
        assertFalse(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/static/private.png")));
        assertFalse(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events/runescape-assets/npc-icon/655/?extra=1")));
        assertFalse(BingoApiClient.productionRouteAllowed(HttpUrl.parse("http://soulrend.events/runescape-assets/npc-icon/655/")));
    }

    @Test
    public void unitTestLocalMockServerContinuesToWork()
    {
        assertTrue(BingoApiClient.productionRouteAllowed(HttpUrl.parse("http://127.0.0.1:8080/api/plugin/config")));
    }
}
