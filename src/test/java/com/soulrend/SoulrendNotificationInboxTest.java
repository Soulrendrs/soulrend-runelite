package com.soulrend;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

/** Shared inbox wire format, sanitized destinations and predictable failure behavior. */
public class SoulrendNotificationInboxTest {
    @Test
    public void parsesAccountInboxAndReadState() {
        String payload = "{\"unread_count\":2,\"next_cursor\":\"signed-keyset\",\"items\":["
            + "{\"id\":17,\"category\":\"proof\",\"kind\":\"proof_rejected\","
            + "\"title\":\"Proof rejected\",\"description\":\"Needs attention\","
            + "\"url\":\"/events/42/\",\"created_at\":\"2026-11-22T18:00:00+00:00\","
            + "\"read_at\":null,\"mention\":false}]}";
        BingoApiClient.NotificationInbox inbox = new Gson().fromJson(payload, BingoApiClient.NotificationInbox.class);
        assertEquals(2, inbox.unread_count);
        assertEquals("signed-keyset", inbox.next_cursor);
        assertEquals(1, inbox.items.size());
        assertNull(inbox.items.get(0).read_at);
        assertEquals(17, inbox.items.get(0).id);
        assertEquals("proof_rejected", inbox.items.get(0).kind);
    }

    @Test
    public void onlySiteLocalPathsMayOpenBrowser() {
        assertEquals("https://soulrend.events/events/3/",
            SoulrendNotificationPanel.safeDestination("/events/3/"));
        assertNull(SoulrendNotificationPanel.safeDestination("https://evil.example/login"));
        assertNull(SoulrendNotificationPanel.safeDestination("//evil.example"));
        assertNull(SoulrendNotificationPanel.safeDestination("/\\evil.example"));
        assertNull(SoulrendNotificationPanel.safeDestination("/events/3/\nLocation: evil"));
        assertNull(SoulrendNotificationPanel.safeDestination(null));
    }

    @Test
    public void malformedTimestampDoesNotBreakRendering() {
        assertEquals("", SoulrendNotificationPanel.relative("invalid"));
        assertEquals("", SoulrendNotificationPanel.relative(null));
    }
}
