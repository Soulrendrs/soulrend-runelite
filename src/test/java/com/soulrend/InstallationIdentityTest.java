package com.soulrend;

import org.junit.Test;
import static org.junit.Assert.*;

/** Installation IDs are correlation labels, never hardware fingerprints or credentials. */
public class InstallationIdentityTest {
    @Test
    public void acceptsOnlyCanonicalRandomVersionFourIdentifiers() {
        String id = java.util.UUID.randomUUID().toString();
        assertTrue(InstallationIdentity.valid(id));
        assertFalse(InstallationIdentity.valid(null));
        assertFalse(InstallationIdentity.valid(""));
        assertFalse(InstallationIdentity.valid(id.toUpperCase(java.util.Locale.ROOT)));
        assertFalse(InstallationIdentity.valid("00000000-0000-0000-0000-000000000000"));
        assertFalse(InstallationIdentity.valid("../some-file"));
    }

    @Test
    public void aMissingPluginDirectoryDoesNotForgeStableDeviceIdentity() {
        InstallationIdentity identity = new InstallationIdentity();
        assertNull(identity.getOrCreate());
    }

    @Test
    public void platformIsAnOptionalLabelNotAHardwareFingerprint() {
        String platform = InstallationIdentity.platform();
        assertTrue(platform.isEmpty() ||
            platform.equals("Windows") || platform.equals("macOS") || platform.equals("Linux"));
        assertEquals("RuneLite installation", InstallationIdentity.defaultName(""));
        assertEquals("RuneLite on Windows", InstallationIdentity.defaultName("Windows"));
    }
}
