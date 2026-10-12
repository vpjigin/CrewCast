package com.solocrew.crewcast.web;

import com.solocrew.crewcast.access.AccessSession;
import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.share.ShareZipDownloadService;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"server.port=0", "crewcast.download-all.max-concurrent=1"})
@AutoConfigureMockMvc
class DownloadAllConcurrencyApiTest extends CrewcastSpringTestBase {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    AccessSession accessSession;
    @Autowired
    SharingState sharingState;
    @Autowired
    ShareRegistry shareRegistry;
    @Autowired
    SettingsStore settingsStore;
    @Autowired
    ShareZipDownloadService zipDownloadService;

    @BeforeEach
    void sharing() throws Exception {
        settingsStore.getSettings().setShares(new java.util.ArrayList<>());
        settingsStore.save();
        var settings = settingsStore.getSettings();
        settings.setRequirePrivateKey(true);
        settingsStore.update(settings);
        sharingState.setSharingActive(true);
        Path dir = Files.createTempDirectory("crewcast-dlall-cap-");
        Files.writeString(dir.resolve("a.txt"), "a", StandardCharsets.UTF_8);
        shareRegistry.addFolder(dir);
    }

    @Test
    void returns429WhenConcurrentLimitReached() throws Exception {
        assertTrue(zipDownloadService.concurrencyGateForTests().tryEnter());
        try {
            mockMvc.perform(get("/api/download-all").param("t", accessSession.getToken()))
                    .andExpect(status().isTooManyRequests());
        } finally {
            zipDownloadService.concurrencyGateForTests().exit();
        }
    }
}
