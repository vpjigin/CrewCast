package com.solocrew.crewcast.media;

import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.PrepareAudioMode;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Downloads real ffmpeg into isolated {@code crewcast.home} (network + ~50–180 MB).
 * <p><b>Opt-in only:</b> run with {@code -Dcrewcast.ffmpeg.e2e=true}. Plain {@code mvn test} skips this.
 */
@Tag("e2e")
@EnabledIf(
        value = "com.solocrew.crewcast.media.FfmpegTestSupport#ffmpegDownloadE2eEnabled",
        disabledReason = "Opt-in only — enable with -Dcrewcast.ffmpeg.e2e=true")
@SpringBootTest(properties = "server.port=0")
class FfmpegDownloaderEndToEndTest extends CrewcastSpringTestBase {

    @Autowired
    FfmpegDownloader ffmpegDownloader;
    @Autowired
    FfmpegManager ffmpegManager;
    @Autowired
    AudioFixService audioFixService;
    @Autowired
    ShareRegistry shareRegistry;
    @Autowired
    SharingState sharingState;
    @Autowired
    SettingsStore settingsStore;

    @BeforeEach
    void cleanBin() throws Exception {
        Path bin = isolatedHome().resolve("bin");
        if (Files.isDirectory(bin)) {
            try (var walk = Files.walk(bin)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
        Files.createDirectories(bin);
        System.setProperty("crewcast.ffmpeg.forceDownload", "true");
        System.setProperty("crewcast.ffmpeg.skipSystemSearch", "true");
        ffmpegManager.recheck();
    }

    @Test
    void downloadVerifyAndTranscodeDualEac3Fixture() throws Exception {
        assumeTrue(
                System.getProperty("os.name", "").toLowerCase().contains("mac"),
                "Real download E2E runs on macOS CI/dev (Martin Riedl or Homebrew-free path)");

        ffmpegDownloader.downloadIfNeeded(true);
        ffmpegManager.recheck();
        assertTrue(ffmpegManager.isAvailable(), "ffmpeg+ffprobe after download");
        assertTrue(Files.isRegularFile(FfmpegDownloader.ffmpegLocalPath()));
        assertTrue(Files.isRegularFile(FfmpegDownloader.ffprobeLocalPath()));

        Path fixture = MediaTestFixtures.coverArt();
        sharingState.setSharingActive(true);
        AppSettings s = settingsStore.getSettings();
        s.setFixAudioForBrowsers(true);
        s.setPrepareAudioInAdvance(PrepareAudioMode.OFF);
        settingsStore.update(s);
        shareRegistry.addFile(fixture);
        audioFixService.clearCache();

        var media = audioFixService.probe(fixture);
        audioFixService.prepareAudio(fixture, 0);
        for (int i = 0; i < 120; i++) {
            if (audioFixService.isTrackReady(fixture, 0)) {
                break;
            }
            Thread.sleep(1000);
        }
        assertTrue(audioFixService.isTrackReady(fixture, 0), "E-AC3 track converted with downloaded ffmpeg");
        assertTrue(media.audioTracks().size() >= 2);
    }
}
