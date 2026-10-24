package com.solocrew.crewcast.media;

import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.support.E2eSpringTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
@E2eFfmpegRequired
class AudioFixIntegrationTest extends E2eSpringTestBase {

    private static Path fixture;
    private static String videoId;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ShareRegistry shareRegistry;
    @Autowired
    SharingState sharingState;
    @Autowired
    SettingsStore settingsStore;
    @Autowired
    AudioFixService audioFixService;
    @Autowired
    FfmpegManager ffmpegManager;

    @DynamicPropertySource
    static void fixtures(DynamicPropertyRegistry registry) throws Exception {
        if (!FfmpegTestSupport.ffmpegAndFfprobeAvailable()) {
            return;
        }
        if (fixture == null) {
            fixture = MediaTestFixtures.coverArt();
        }
        videoId = ShareRegistry.idForPath(fixture);
    }

    @org.junit.jupiter.api.AfterEach
    void restoreSettings() {
        AppSettings s = settingsStore.getSettings();
        s.setRequirePrivateKey(true);
        settingsStore.update(s);
    }

    @BeforeEach
    void sharingOn() throws Exception {
        settingsStore.getSettings().setShares(new java.util.ArrayList<>());
        settingsStore.save();
        sharingState.setSharingActive(true);
        AppSettings s = settingsStore.getSettings();
        s.setFixAudioForBrowsers(true);
        s.setPrepareAudioInAdvance(com.solocrew.crewcast.settings.PrepareAudioMode.OFF);
        s.setRequirePrivateKey(false);
        settingsStore.update(s);
        ffmpegManager.recheck();
        shareRegistry.addFile(fixture);
        videoId = shareRegistry.listVideos().getFirst().id();
    }

    @Test
    void streamReturns503Then206WhenReady() throws Exception {
        mockMvc.perform(get("/stream/" + videoId + "?audio=0"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(post("/api/videos/" + videoId + "/audio/prepare?audio=0"))
                .andExpect(status().isOk());
        long deadline = System.currentTimeMillis() + 180_000;
        boolean ready = false;
        while (System.currentTimeMillis() < deadline) {
            AudioFixStatus st = audioFixService.statusFor(fixture, 0);
            if (st.status() == AudioFixJobState.READY) {
                ready = true;
                break;
            }
            if (st.status() == AudioFixJobState.FAILED) {
                org.junit.jupiter.api.Assertions.fail("conversion failed: " + st.message());
            }
            Thread.sleep(500);
        }
        assertTrue(ready, "conversion did not finish in time");

        Path cached = audioFixService.resolvePlayback(fixture, 0).file().orElseThrow();
        assertTrue(Files.isRegularFile(cached));
        assertTrue(Files.size(cached) > 1024);

        ProbedMedia out = MediaProbe.probe(cached, ffmpegManager.ffprobe());
        assertTrue(out.audioTracks().stream().anyMatch(a -> "aac".equals(a.codecName())));

        long fileLen = Files.size(cached);
        mockMvc.perform(get("/stream/" + videoId + "?audio=0").header("Range", "bytes=0-"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-" + (fileLen - 1) + "/" + fileLen));

        mockMvc.perform(get("/stream/" + videoId + "?audio=0").header("Range", "bytes=0-1"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "2"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-1/" + fileLen));
    }

    @Test
    void h264StreamReturnsValidMp4Bytes() throws Exception {
        Path h264 = MediaTestFixtures.h264();
        shareRegistry.addFile(h264);
        String id = shareRegistry.listVideos().stream()
                .filter(v -> v.path().equals(h264))
                .findFirst()
                .orElseThrow()
                .id();
        byte[] head = mockMvc.perform(get("/stream/" + id + "?audio=0"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertTrue(head.length > 8);
        assertEquals('f', (char) head[4]);
        assertEquals('t', (char) head[5]);
        assertEquals('y', (char) head[6]);
        assertEquals('p', (char) head[7]);
    }

    @Test
    void stalePartFilesRemovedOnStartup() throws Exception {
        Path part = com.solocrew.crewcast.system.CrewcastPaths.cacheDir().resolve("stale.part");
        Files.createDirectories(part.getParent());
        Files.writeString(part, "x");
        audioFixService.cleanupStaleParts();
        assertTrue(Files.notExists(part));
    }
}
