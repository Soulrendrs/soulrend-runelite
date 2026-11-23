package com.soulrend;

import java.lang.reflect.Proxy;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DisabledFeaturesTest {

    private final SoulrendPlugin plugin = new SoulrendPlugin();

    public DisabledFeaturesTest() {
        // RuneLite only provides values for methods with a ConfigItem annotation.
        plugin.config = (SoulrendConfig) Proxy.newProxyInstance(SoulrendConfig.class.getClassLoader(),
            new Class<?>[] {SoulrendConfig.class}, (proxy, method, args) -> {
                if (method.isAnnotationPresent(ConfigItem.class) && method.getName().equals("chatFeed")) {
                    return false;
                }
                throw new AssertionError("Unexpected config read: " + method.getName());
            });
    }

    @Test
    public void pushAndRelayPresenceDoNotReadRemovedSettings() {
        plugin.syncPush();
        assertEquals(0, plugin.relayPresence());
        assertFalse(plugin.relaySay("public", "test", null));
    }

    @Test
    public void collectionLogFlushesSkipProfileSyncForSignedInCharacter() {
        plugin.profileSyncRsn = "Test player";
        assertFalse(plugin.supportsProfileSync());
        plugin.flushClogSync();
        plugin.flushFullClogSync();
    }
}
