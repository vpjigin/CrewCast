package com.solocrew.crewcast.media;

import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.facade.ShareListItem;
import com.solocrew.crewcast.facade.SharingService;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.PrepareCompatMode;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import com.solocrew.crewcast.system.CrewcastPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
@Import(HostOwnedConversionTest.StubAudioConfig.class)
class CompatUiProgressRefreshTest extends CrewcastSpringTestBase {

    @Autowired
    CompatFixService compatFixService;

    @Autowired
    SharingService sharingService;

    @Autowired
    ShareRegistry shareRegistry;

    @Autowired
    SettingsStore settingsStore;

    @Autowired
    SharingState sharingState;

    @Autowired
    MockMvc mockMvc;

    String videoId;

    @BeforeEach
    void setUp() throws Exception {
        compatFixService.cancelAll(true);
        if (Files.isDirectory(CrewcastPaths.cacheDir())) {
            try (var stream = Files.list(CrewcastPaths.cacheDir())) {
                stream.forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
        HostOwnedConversionTest.media = new ProbedMedia(
                0,
                "hevc",
                true,
                List.of(new AudioStreamInfo(0, 0, "aac", "eng", "", 2, "stereo")),
                List.of(),
                VideoFormat.unknown());
        HostOwnedConversionTest.PROBES.clear();
        sharingState.setSharingActive(true);
        AppSettings s = settingsStore.getSettings();
        s.setRequirePrivateKey(false);
        s.setAllowViewerCompatPrepare(true);
        s.setPrepareCompatInAdvance(PrepareCompatMode.ALL_VIDEOS);
        settingsStore.update(s);
        compatFixService.testSetResolvedEncoder("libx264");

        Path f = CrewcastPaths.home().resolve("progress.mkv");
        Files.writeString(f, "video-bytes");
        HostOwnedConversionTest.registerProbe(f);
        shareRegistry.addFile(f);
        videoId = shareRegistry.listVideos().getFirst().id();
    }

    @Test
    void shareRowRefreshFollowsQueuedRunningReady() throws Exception {
        List<ShareListItem> items = sharingService.listShares();
        Path video = Path.of(items.getFirst().entry().getPath());
        compatFixService.testInstallJob(video, HostOwnedConversionTest.media, 0, AudioFixJobState.QUEUED, 0);
        items = sharingService.refreshCompatFieldsOnItems(items);
        assertEquals(CompatUiState.QUEUED, items.getFirst().fileCompatState());

        compatFixService.testInstallJob(video, HostOwnedConversionTest.media, 0, AudioFixJobState.RUNNING, 42);
        items = sharingService.refreshCompatFieldsOnItems(items);
        assertEquals(CompatUiState.CONVERTING, items.getFirst().fileCompatState());
        assertEquals(42, items.getFirst().fileCompatProgress());

        compatFixService.testInstallJob(video, HostOwnedConversionTest.media, 0, AudioFixJobState.READY, 100);
        items = sharingService.refreshCompatFieldsOnItems(items);
        assertEquals(CompatUiState.READY, items.getFirst().fileCompatState());
        assertEquals(100, items.getFirst().fileCompatProgress());
    }

    @Test
    void progressListenerCanBeObserved() {
        AtomicInteger ticks = new AtomicInteger();
        compatFixService.addProgressListener(ticks::incrementAndGet);
        compatFixService.testFireProgressListeners();
        assertEquals(1, ticks.get());
    }

    @Test
    void webCompatStatusShowsRunningWithProgress() throws Exception {
        Path videoPath = shareRegistry.findById(videoId).orElseThrow().path();
        compatFixService.testInstallJob(
                videoPath, HostOwnedConversionTest.media, 0, AudioFixJobState.RUNNING, 55);

        mockMvc.perform(get("/api/videos/" + videoId + "/compat-status?audio=0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.progress").value(55));
    }
}
