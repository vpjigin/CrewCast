package com.solocrew.crewcast.media;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
@E2eFfmpegRequired
class SilentVideoStreamTest extends CrewcastSpringTestBase {

    private static Path silentFixture;
    private static String videoId;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ShareRegistry shareRegistry;
    @Autowired
    SharingState sharingState;
    @Autowired
    SettingsStore settingsStore;

    @DynamicPropertySource
    static void fixtures(DynamicPropertyRegistry registry) throws Exception {
        if (!FfmpegTestSupport.ffmpegAndFfprobeAvailable()) {
            return;
        }
        if (silentFixture == null) {
            silentFixture = MediaTestFixtures.silentH264();
        }
        videoId = ShareRegistry.idForPath(silentFixture);
    }

    @BeforeEach
    void sharingOn() throws Exception {
        settingsStore.getSettings().setShares(new java.util.ArrayList<>());
        settingsStore.save();
        sharingState.setSharingActive(true);
        AppSettings s = settingsStore.getSettings();
        s.setFixAudioForBrowsers(true);
        s.setRequirePrivateKey(false);
        settingsStore.update(s);
        shareRegistry.addFile(silentFixture);
        videoId = shareRegistry.listVideos().getFirst().id();
    }

    @Test
    void apiListsNoAudioTracks() throws Exception {
        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audioTracks", empty()));
    }

    @Test
    void streamWithoutAudioTrackServesOriginalMp4WithRange() throws Exception {
        mockMvc.perform(get("/stream/" + videoId + "?audio=0")
                        .header(HttpHeaders.RANGE, "bytes=0-1023"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.parseMediaType("video/mp4").toString()))
                .andExpect(header().exists(HttpHeaders.CONTENT_RANGE))
                .andExpect(content().contentTypeCompatibleWith("video/mp4"));

        mockMvc.perform(get("/stream/" + videoId))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(content().contentTypeCompatibleWith("video/mp4"));
    }
}
