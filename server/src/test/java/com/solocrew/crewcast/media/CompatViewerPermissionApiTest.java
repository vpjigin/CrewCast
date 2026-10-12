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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
class CompatViewerPermissionApiTest extends CrewcastSpringTestBase {

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

    @DynamicPropertySource
    static void fixtures(DynamicPropertyRegistry registry) throws Exception {
        if (fixture == null) {
            fixture = MediaTestFixtures.h264();
        }
        videoId = ShareRegistry.idForPath(fixture);
    }

    @BeforeEach
    void setup() throws Exception {
        settingsStore.getSettings().setShares(new java.util.ArrayList<>());
        settingsStore.save();
        sharingState.setSharingActive(true);
        AppSettings s = settingsStore.getSettings();
        s.setRequirePrivateKey(false);
        s.setAllowViewerCompatPrepare(false);
        settingsStore.update(s);
        shareRegistry.addFile(fixture);
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
}
