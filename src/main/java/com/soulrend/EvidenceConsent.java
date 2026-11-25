package com.soulrend;

/** Consent is versioned and scoped to the authenticated Soulrend device session. */
final class EvidenceConsent {
    static final String KEY = "evidenceConsentV1";

    private EvidenceConsent() {}

    static String marker(String token) {
        return DiskFiles.fingerprint(token);
    }

    static boolean reviewed(String token, String stored) {
        String expected = marker(token);
        return !expected.isEmpty() && expected.equals(stored);
    }

    static boolean enabled(boolean setting, String token, String stored) {
        return setting && reviewed(token, stored);
    }
}
