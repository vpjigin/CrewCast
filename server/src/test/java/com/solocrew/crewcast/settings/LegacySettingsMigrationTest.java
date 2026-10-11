package com.solocrew.crewcast.settings;

import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import com.solocrew.crewcast.system.CrewcastPaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "server.port=0")
class LegacySettingsMigrationTest extends CrewcastSpringTestBase {

    @Autowired
    SettingsStore settingsStore;

    @Test
    void legacyAccessTokenFieldIsDroppedOnSave() throws Exception {
        Path settingsPath = CrewcastPaths.settingsFile();
        Files.createDirectories(settingsPath.getParent());
        Files.writeString(
                settingsPath,
                """
                        {
                          "accessToken" : "deadbeefdeadbeefdeadbeefdeadbeef",
                          "requirePrivateKey" : true
                        }
                        """);

        settingsStore.load();
        assertTrue(settingsStore.getSettings().isRequirePrivateKey());
        settingsStore.save();

        String saved = Files.readString(settingsPath);
        assertFalse(saved.contains("accessToken"), "access token must not be persisted");
        assertFalse(saved.contains("deadbeef"), "token value must not be persisted");
        assertTrue(saved.contains("requirePrivateKey"), "normal settings should remain");
    }
}
