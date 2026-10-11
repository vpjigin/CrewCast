package com.solocrew.crewcast.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** One-time migration from the pre-rename data directory ({@code ~/.lanstream}). */
public final class CrewcastLegacyMigration {

    /** Legacy directory name before the Crewcast rename; only referenced here for migration. */
    public static final String LEGACY_HOME_DIR = ".lanstream";

    private static final Logger LOG = LoggerFactory.getLogger(CrewcastLegacyMigration.class);

    private CrewcastLegacyMigration() {
    }

    /**
     * If {@code ~/.crewcast} has no settings yet but {@code ~/.lanstream/settings.json} exists,
     * copy that file only (not cache or bin). The legacy folder is never modified or deleted.
     */
    public static void migrateSettingsIfNeeded() {
        Path newHome = defaultCrewcastHome();
        Path legacyHome = legacyHomeDir();
        Path newSettings = newHome.resolve("settings.json");
        Path legacySettings = legacyHome.resolve("settings.json");
        try {
            if (Files.exists(newSettings) || !Files.isRegularFile(legacySettings)) {
                return;
            }
            Files.createDirectories(newHome);
            CrewcastWriteGuard.check(newSettings);
            Files.copy(legacySettings, newSettings, StandardCopyOption.COPY_ATTRIBUTES);
            restrictOwnerOnly(newSettings);
            LOG.info(
                    "Migrated settings from {} to {} (legacy {} left unchanged)",
                    legacySettings,
                    newSettings,
                    legacyHome);
        } catch (IOException e) {
            LOG.warn("Could not migrate settings from {}: {}", legacyHome, e.getMessage());
        }
    }

    static Path legacyHomeDir() {
        return Path.of(System.getProperty("user.home"), LEGACY_HOME_DIR).toAbsolutePath().normalize();
    }

    private static Path defaultCrewcastHome() {
        String prop = System.getProperty("crewcast.home");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".crewcast").toAbsolutePath().normalize();
    }

    private static void restrictOwnerOnly(Path file) {
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
                Files.setPosixFilePermissions(
                        file,
                        java.util.EnumSet.of(
                                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            }
        } catch (Exception ignored) {
        }
    }
}
