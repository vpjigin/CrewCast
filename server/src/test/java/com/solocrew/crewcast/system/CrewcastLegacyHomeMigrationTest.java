package com.solocrew.crewcast.system;

import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrewcastLegacyHomeMigrationTest extends CrewcastSpringTestBase {

    @TempDir
    Path tempRoot;

    @Test
    void copiesLegacySettingsJsonOnlyWhenNewHomeMissing() throws Exception {
        Path fakeHome = tempRoot.resolve("homes");
        Path legacy = fakeHome.resolve(CrewcastLegacyMigration.LEGACY_HOME_DIR);
        Path crewcast = fakeHome.resolve(".crewcast");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("settings.json"), "{\"requirePrivateKey\":true}\n");
        Files.createDirectories(legacy.resolve("cache"));
        Files.writeString(legacy.resolve("cache/x.part"), "part");

        String priorHome = System.getProperty("user.home");
        String priorCrewcastHome = System.getProperty("crewcast.home");
        try {
            System.setProperty("user.home", fakeHome.toString());
            System.setProperty("crewcast.home", crewcast.toString());
            CrewcastWriteGuard.setEnabled(true);

            CrewcastLegacyMigration.migrateSettingsIfNeeded();
        } finally {
            if (priorHome != null) {
                System.setProperty("user.home", priorHome);
            } else {
                System.clearProperty("user.home");
            }
            if (priorCrewcastHome != null) {
                System.setProperty("crewcast.home", priorCrewcastHome);
            } else {
                System.clearProperty("crewcast.home");
            }
        }

        assertTrue(Files.isRegularFile(crewcast.resolve("settings.json")));
        assertEquals(
                "{\"requirePrivateKey\":true}\n",
                Files.readString(crewcast.resolve("settings.json")));
        assertTrue(Files.isRegularFile(legacy.resolve("settings.json")));
        assertTrue(Files.exists(legacy.resolve("cache/x.part")));
        assertTrue(!Files.exists(crewcast.resolve("cache")));
    }

}
