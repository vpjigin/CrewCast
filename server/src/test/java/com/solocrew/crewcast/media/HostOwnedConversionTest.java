package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import com.solocrew.crewcast.system.CrewcastPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "server.port=0")
@Import(HostOwnedConversionTest.StubAudioConfig.class)
class HostOwnedConversionTest extends CrewcastSpringTestBase {

    static final Map<Path, ProbedMedia> PROBES = new ConcurrentHashMap<>();
    static ProbedMedia media;

    @Autowired
    CompatFixService compatFixService;

    @Autowired
    SettingsStore settingsStore;

    @Autowired
    com.solocrew.crewcast.facade.SharingService sharingService;

    @BeforeEach
    void stubProbe() throws Exception {
        compatFixService.cancelAll(true);
        media = new ProbedMedia(
                0,
                "hevc",
                true,
                List.of(new AudioStreamInfo(1, 0, "aac", "eng", "", 2, "stereo")),
                List.of(),
                VideoFormat.unknown());
        PROBES.clear();
        AppSettings s = settingsStore.getSettings();
        s.setAllowViewerCompatPrepare(true);
        settingsStore.update(s);
        compatFixService.testSetResolvedEncoder("libx264");
    }

    static void registerProbe(Path file) {
        PROBES.put(file.toAbsolutePath().normalize(), media);
    }

    @Test
    void staleCompatPartRemovedOnStartup() throws Exception {
        Path part = CrewcastPaths.cacheDir().resolve("compat-deadbeef.mp4.part");
        Files.writeString(part, "partial");
        compatFixService.startup();
        assertFalse(Files.exists(part));
    }

    @Test
    void viewerQueueLimitCountsQueuedOnlyNotRunning() throws Exception {
        Path base = CrewcastPaths.home();
        Path running = base.resolve("running.mkv");
        Files.writeString(running, "r");
        registerProbe(running);
        compatFixService.testEnqueue(running, media, 0, true, "A");
        compatFixService.testSetJobState(running, 0, AudioFixJobState.RUNNING);
        for (int i = 0; i < 3; i++) {
            Path file = base.resolve("w" + i + ".mkv");
            Files.writeString(file, "a");
            registerProbe(file);
            compatFixService.testEnqueue(file, media, 0, true, "Viewer " + i);
        }
        assertEquals(3, compatFixService.viewerQueuedCount());
        Path extra = base.resolve("w4.mkv");
        Files.writeString(extra, "b");
        registerProbe(extra);
        compatFixService.testSetResolvedEncoder("libx264");
        AudioFixStatus st = compatFixService.prepareCompat(extra, 0, true, "Extra");
        assertEquals(AudioFixJobState.FAILED, st.status());
        assertEquals(ConversionLimits.HOST_BUSY_MESSAGE, st.message());
    }

    @Test
    void viewerQueueLimitReturnsBusyMessage() throws Exception {
        Path base = CrewcastPaths.home();
        for (int i = 0; i < 3; i++) {
            Path file = base.resolve("q" + i + ".mkv");
            Files.writeString(file, "a");
            registerProbe(file);
            compatFixService.testEnqueue(file, media, 0, true, "Viewer " + i);
        }
        assertEquals(3, compatFixService.viewerQueuedCount());
        Path extra = base.resolve("q4.mkv");
        Files.writeString(extra, "b");
        registerProbe(extra);
        AudioFixStatus st = compatFixService.prepareCompat(extra, 0, true, "Extra");
        assertEquals(AudioFixJobState.FAILED, st.status());
        assertEquals(ConversionLimits.HOST_BUSY_MESSAGE, st.message());
    }

    @Test
    void secondPrepareJoinsExistingJob() throws Exception {
        Path f = CrewcastPaths.home().resolve("one.mkv");
        Files.writeString(f, "x");
        registerProbe(f);
        compatFixService.testEnqueue(f, media, 0, true, "A");
        compatFixService.testEnqueue(f, media, 0, true, "B");
        assertEquals(1, compatFixService.activeJobCount());
    }

    @Test
    void queuedJobSurvivesWithoutStatusPolls() throws Exception {
        Path f = CrewcastPaths.home().resolve("idle.mkv");
        Files.writeString(f, "x");
        registerProbe(f);
        compatFixService.testEnqueue(f, media, 0, true, "Viewer");
        assertEquals(1, compatFixService.activeJobCount());
        Thread.sleep(500);
        assertEquals(1, compatFixService.activeJobCount());
    }

    @Test
    void stopSharingCancelsViewerJobsOnly() throws Exception {
        Path hostFile = CrewcastPaths.home().resolve("host.mkv");
        Path viewerFile = CrewcastPaths.home().resolve("viewer.mkv");
        Files.writeString(hostFile, "h");
        Files.writeString(viewerFile, "v");
        registerProbe(hostFile);
        registerProbe(viewerFile);
        compatFixService.testInstallJob(hostFile, media, 0, AudioFixJobState.RUNNING, 40);
        compatFixService.testEnqueue(viewerFile, media, 0, true, "Viewer");
        assertEquals(2, compatFixService.activeJobCount());
        sharingService.stopSharing();
        assertEquals(1, compatFixService.activeJobCount());
        AudioFixStatus host = compatFixService.statusFor(hostFile, 0);
        assertEquals(AudioFixJobState.RUNNING, host.status());
        assertEquals(40, host.progress());
        compatFixService.cancelAll(true);
    }

    @Test
    void cancelRemovesPartFile() throws Exception {
        Path f = CrewcastPaths.home().resolve("cancel.mkv");
        Files.writeString(f, "x");
        registerProbe(f);
        compatFixService.testEnqueue(f, media, 0, false, null);
        Path out = CrewcastPaths.cacheDir().resolve(
                CompatCacheKeys.cacheFileName(f, 0, 1080));
        Path part = ConversionPartFiles.partPath(out);
        Files.writeString(part, "partial");
        compatFixService.cancelForVideo(f);
        assertFalse(Files.exists(part));
    }

    @TestConfiguration
    static class StubAudioConfig {

        @Bean
        @Primary
        AudioFixService audioFixService(FfmpegManager ffmpegManager, SettingsStore settingsStore, ShareRegistry shareRegistry) {
            return new AudioFixService(ffmpegManager, settingsStore, shareRegistry) {
                @Override
                public ProbedMedia probe(Path file) throws IOException, InterruptedException {
                    Path key = file.toAbsolutePath().normalize();
                    ProbedMedia m = PROBES.get(key);
                    if (m != null) {
                        return m;
                    }
                    return HostOwnedConversionTest.media;
                }
            };
        }
    }
}
