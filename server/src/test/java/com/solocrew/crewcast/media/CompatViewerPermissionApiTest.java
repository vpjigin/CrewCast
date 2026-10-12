package com.solocrew.crewcast.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
@Import(CompatViewerPermissionApiTest.StubAudioFixConfig.class)
class CompatViewerPermissionApiTest extends CrewcastSpringTestBase {

    static final ProbedMedia SAMPLE_MEDIA = new ProbedMedia(
            0,
            "h264",
            false,
            List.of(new AudioStreamInfo(1, 0, "aac", "eng", "", 2, "stereo")),
            List.of(),
            new VideoFormat(320, 240, 24, "yuv420p", "High", 8, "", ""));

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ShareRegistry shareRegistry;
    @Autowired
    SharingState sharingState;
    @Autowired
    SettingsStore settingsStore;

    private String videoId;

    @BeforeEach
    void setup() throws Exception {
        settingsStore.getSettings().setShares(new java.util.ArrayList<>());
        settingsStore.save();
        sharingState.setSharingActive(true);
        AppSettings s = settingsStore.getSettings();
        s.setRequirePrivateKey(false);
        s.setAllowViewerCompatPrepare(false);
        settingsStore.update(s);

        Path stub = Files.createTempFile("compat-perm-", ".mp4");
        Files.write(stub, new byte[] {0x00});
        shareRegistry.addFile(stub);
        videoId = shareRegistry.listVideos().getFirst().id();
    }

    @Test
    void videoDetailsExposeViewerPermissionAndState() throws Exception {
        String body = mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = new ObjectMapper().readTree(body);
        assertFalse(json.get("compatViewerPrepareAllowed").asBoolean());
        assertEquals("NOT_NEEDED", json.get("compatState").asText());
        assertTrue(json.has("risky"));
        assertTrue(json.has("codec"));
        assertTrue(json.has("hasAudio"));
        assertTrue(json.get("hasAudio").asBoolean());
    }

    @Test
    void prepareCompatBlockedWhenViewerNotAllowed() throws Exception {
        String body = mockMvc.perform(post("/api/videos/" + videoId + "/compat/prepare?audio=0"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = new ObjectMapper().readTree(body);
        assertEquals("FAILED", json.get("status").asText());
        assertEquals(CompatFixService.VIEWER_PREPARE_BLOCKED_MESSAGE, json.get("message").asText());
    }

    @TestConfiguration
    static class StubAudioFixConfig {

        @Bean
        @Primary
        AudioFixService audioFixService(FfmpegManager ffmpegManager, SettingsStore settingsStore, ShareRegistry shareRegistry) {
            return new AudioFixService(ffmpegManager, settingsStore, shareRegistry) {
                @Override
                public ProbedMedia probe(Path file) {
                    return SAMPLE_MEDIA;
                }

                @Override
                public int defaultAudioIndex(ProbedMedia media) {
                    return 0;
                }

                @Override
                public AudioFixStatus statusFor(Path source, int audioIndex, boolean recordPoll) {
                    return new AudioFixStatus(AudioFixJobState.READY, 100, "Ready", "test");
                }

                @Override
                public String prepareTimeHint(Path source) {
                    return "a few minutes";
                }

                @Override
                public boolean isTrackReady(Path source, int audioIndex) {
                    return true;
                }
            };
        }
    }
}
