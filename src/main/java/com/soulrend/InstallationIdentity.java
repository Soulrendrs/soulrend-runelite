package com.soulrend;

import java.io.IOException;
import java.util.UUID;
import net.runelite.client.util.Filepath;

/**
 * Random, non-secret correlation ID for this local Soulrend plugin installation.
 *
 * Lives under Soulrend's own plugin directory, not in profile-synced config,
 * never reads a MAC, hostname, IP or another plugin's data. Copying the plugin
 * directory also copies the ID; each authorization still has an independent
 * server-issued token and can be revoked separately.
 *
 * Disk access is only performed by DeviceSignIn's off-EDT worker.
 */
final class InstallationIdentity {
    private Filepath directory;
    private String cached;

    synchronized void setDirectory(Filepath directory) {
        this.directory = directory;
        this.cached = null;
    }

    static boolean valid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.version() == 4 && uuid.toString().equals(value);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    synchronized String getOrCreate() {
        if (cached != null) return cached;
        if (directory == null || DiskFiles.ensureDir(directory) == null) return null;
        Filepath path = directory.joinSegment("installation-id");
        String value = DiskFiles.readText(path);
        if (value != null) value = value.trim();
        if (valid(value)) return cached = value;
        value = UUID.randomUUID().toString();
        try {
            DiskFiles.writeAtomic(path, value);
            cached = value;
            return cached;
        } catch (IOException | RuntimeException ex) {
            return null; // Never promise stable identity if the disk write failed.
        }
    }

    static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("windows")) return "Windows";
        if (os.contains("mac") || os.contains("darwin")) return "macOS";
        if (os.contains("linux")) return "Linux";
        return "";
    }

    static String defaultName(String platform) {
        return platform.isEmpty() ? "RuneLite installation" : "RuneLite on " + platform;
    }
}
