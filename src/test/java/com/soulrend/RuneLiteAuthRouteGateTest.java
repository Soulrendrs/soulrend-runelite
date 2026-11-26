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
    public void onlyReviewedProductionPathsAndMethodsAreAllowed()
    {
        String base = "https://soulrend.events/api/runelite/v1/";
        assertTrue(BingoApiClient.productionRequestAllowed(HttpUrl.parse(base + "events/12/evidence"), "POST"));
        assertTrue(BingoApiClient.productionRequestAllowed(HttpUrl.parse(base + "events/12/board?view=completions"), "GET"));
        assertFalse(BingoApiClient.productionRequestAllowed(HttpUrl.parse(base + "events/12/board"), "POST"));
        assertFalse(BingoApiClient.productionRequestAllowed(HttpUrl.parse(base + "device/start"), "GET"));
        assertFalse(BingoApiClient.productionRequestAllowed(HttpUrl.parse(base + "unknown"), "POST"));
        assertFalse(BingoApiClient.productionRequestAllowed(HttpUrl.parse(base + "events/12/evidence"), "DELETE"));
        assertFalse(BingoApiClient.productionRequestAllowed(HttpUrl.parse(base + "events/12/evidence/extra"), "GET"));
    }

    @Test
    public void productionRejectsLegacyWebSocketsBeforeAnyNetworkCall()
    {
        String previous = BingoApiClient.site();
        try {
            BingoApiClient.useDevSite(BingoApiClient.CANONICAL_SITE);
            var api = new BingoApiClient(new com.google.gson.Gson(), new okhttp3.OkHttpClient());
            api.configure("test-only-token");
            org.junit.Assert.assertNull(api.fetchPushTicket(true));
            var ticket = new BingoApiClient.PushTicket();
            ticket.ticket = "test-only-ticket";
            for (String url : new String[] {
                "wss://soulrend.events/api/plugin/push/ws",
                "wss://soulrend.events/api/runelite/v1/me",
                "wss://other.example/api/plugin/push/ws"
            }) {
                ticket.url = url;
                try {
                    api.openPush(ticket, new okhttp3.WebSocketListener() {});
                    org.junit.Assert.fail("Unexpected WebSocket: " + url);
                } catch (IllegalStateException expected) {
                    org.junit.Assert.assertEquals("Legacy push is disabled on Soulrend", expected.getMessage());
                }
            }
        } finally {
            BingoApiClient.useDevSite(previous);
        }
    }

    @Test
    public void rejectsNonProductionOrigins()
    {
        assertFalse(BingoApiClient.productionRouteAllowed(HttpUrl.parse("http://127.0.0.1:8080/api/plugin/config")));
        assertFalse(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://evil.example/api/runelite/v1/me")));
        assertFalse(BingoApiClient.productionRouteAllowed(HttpUrl.parse("http://soulrend.events/api/runelite/v1/me")));
        assertFalse(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://soulrend.events:8443/api/runelite/v1/me")));
        assertFalse(BingoApiClient.productionRouteAllowed(HttpUrl.parse("https://user@soulrend.events/api/runelite/v1/me")));
        assertTrue(BingoApiClient.allowedOrigin(HttpUrl.parse("http://127.0.0.1:8080/test"), HttpUrl.parse("http://127.0.0.1:8080")));
    }
}
