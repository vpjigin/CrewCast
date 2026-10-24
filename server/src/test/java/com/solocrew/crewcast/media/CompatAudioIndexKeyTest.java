package com.solocrew.crewcast.media;

import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.AppSettings;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
@Import(HostOwnedConversionTest.StubAudioConfig.class)
class CompatAudioIndexKeyTest extends CrewcastSpringTestBase {

    @Autowired
    AudioFixService audioFixService;

    @Autowired
    CompatFixService compatFixService;

    @Autowired
    SettingsStore settingsStore;

    @Autowired
    ShareRegistry shareRegistry;

    @Autowired
    SharingState sharingState;

    @Autowired
    MockMvc mockMvc;

    Path videoPath;
    String videoId;
    ProbedMedia twoTrackMedia;

    @BeforeEach
    void setUp() throws Exception {
        compatFixService.cancelAll(true);
        twoTrackMedia = new ProbedMedia(
                0,
                "hevc",
                true,
                List.of(
                        new AudioStreamInfo(1, 0, "aac", "hi", "", 2, "stereo"),
                        new AudioStreamInfo(2, 1, "aac", "ml", "", 2, "stereo")),
                List.of(),
                VideoFormat.unknown());
        HostOwnedConversionTest.media = twoTrackMedia;
        HostOwnedConversionTest.PROBES.clear();

        AppSettings s = settingsStore.getSettings();
        s.setPreferredAudioLanguage("ml");
        s.setRequirePrivateKey(false);
        s.setAllowViewerCompatPrepare(true);
        settingsStore.update(s);
        sharingState.setSharingActive(true);
        compatFixService.testSetResolvedEncoder("libx264");

        videoPath = CrewcastPaths.home().resolve("dual-audio.mkv");
        Files.writeString(videoPath, "x");
        HostOwnedConversionTest.registerProbe(videoPath);
        shareRegistry.addFile(videoPath);
        videoId = shareRegistry.listVideos().getFirst().id();
    }

    @Test
    void prepareStatusAndStreamShareCompatCacheKey() throws Exception {
        int canonical = audioFixService.resolveAudioIndex(twoTrackMedia, null);
        assertEquals(1, canonical);

        compatFixService.testInstallJob(videoPath, twoTrackMedia, canonical, AudioFixJobState.RUNNING, 25);

        AudioFixStatus fromDefault = compatFixService.statusFor(videoPath, (Integer) null);
        AudioFixStatus fromExplicit = compatFixService.statusFor(videoPath, canonical);
        AudioFixService.ServeResult stream = compatFixService.resolvePlayback(videoPath, (Integer) null);

        assertEquals(AudioFixJobState.RUNNING, fromDefault.status());
        assertEquals(25, fromDefault.progress());
        assertEquals(fromDefault.cacheKey(), fromExplicit.cacheKey());
        assertEquals(fromDefault.cacheKey(), stream.status().cacheKey());
    }

    @Test
    void wrongExplicitTrackDoesNotSeeDefaultJob() throws Exception {
        int canonical = audioFixService.resolveAudioIndex(twoTrackMedia, null);
        compatFixService.testInstallJob(videoPath, twoTrackMedia, canonical, AudioFixJobState.RUNNING, 10);

        AudioFixStatus otherTrack = compatFixService.statusFor(videoPath, 0);
        assertEquals(AudioFixJobState.NOT_PREPARED, otherTrack.status());
        assertNotEquals(
                compatFixService.statusFor(videoPath, (Integer) null).cacheKey(),
                otherTrack.cacheKey());
    }

    @Test
    void invalidAudioIndexReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/videos/" + videoId + "/compat-status?audio=9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void compatStatusWithoutAudioParamMatchesDefaultTrack() throws Exception {
        Path registryPath = shareRegistry.findById(videoId).orElseThrow().path();
        int canonical = audioFixService.resolveAudioIndex(twoTrackMedia, null);
        compatFixService.testInstallJob(registryPath, twoTrackMedia, canonical, AudioFixJobState.RUNNING, 33);

        mockMvc.perform(get("/api/videos/" + videoId + "/compat-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.progress").value(33));
    }
}
