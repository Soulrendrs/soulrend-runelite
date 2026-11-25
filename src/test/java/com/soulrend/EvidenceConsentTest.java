package com.soulrend;

import org.junit.Test;
import static org.junit.Assert.*;

public class EvidenceConsentTest {
    @Test public void defaultsAndExistingSettingsFailClosedWithoutConsent() {
        assertFalse(new SoulrendConfig() {}.automaticEvidence());
        assertFalse(EvidenceConsent.enabled(true, "old-token", null));
        assertFalse(EvidenceConsent.enabled(true, "old-token", ""));
    }

    @Test public void explicitChoiceIsScopedAndRevocable() {
        String marker = EvidenceConsent.marker("token-a");
        assertTrue(EvidenceConsent.reviewed("token-a", marker));
        assertTrue(EvidenceConsent.enabled(true, "token-a", marker));
        assertFalse(EvidenceConsent.enabled(false, "token-a", marker));
        assertFalse(EvidenceConsent.enabled(true, "token-b", marker));
        assertFalse(EvidenceConsent.enabled(true, "", marker));
        assertFalse(EvidenceConsent.enabled(true, null, null));
        assertNotEquals("token-a", marker);
    }
}
