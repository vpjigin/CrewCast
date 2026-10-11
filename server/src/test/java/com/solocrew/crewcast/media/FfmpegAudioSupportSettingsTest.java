package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.FfmpegDownloadConsent;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import com.solocrew.crewcast.system.CrewcastPaths;
import com.solocrew.crewcast.system.CrewcastWriteGuard;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegAudioSupportSettingsTest extends CrewcastSpringTestBase {

    @Test
    void notNowTurnsFixAudioOffAndPersists() throws Exception {
        Path home = Files.createTempDirectory("crewcast-decline-home");
        System.setProperty("crewcast.home", home.toString());
        CrewcastWriteGuard.setEnabled(true);

        SettingsStore store = new SettingsStore();
        store.load();
        AppSettings s = store.getSettings();
        s.setFixAudioForBrowsers(true);
        FfmpegAudioSupport.applyUserDeclined(s);
        store.update(s);

        assertTrue(Files.exists(CrewcastPaths.settingsFile()));
        assertTrue(CrewcastPaths.settingsFile().startsWith(home));

        SettingsStore reloaded = new SettingsStore();
        reloaded.load();
        assertFalse(reloaded.getSettings().isFixAudioForBrowsers());
        assertEquals(FfmpegDownloadConsent.DECLINED, reloaded.getSettings().getFfmpegDownloadConsent());
    }
}
