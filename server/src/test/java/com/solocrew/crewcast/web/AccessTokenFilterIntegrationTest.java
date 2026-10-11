package com.solocrew.crewcast.web;

import com.solocrew.crewcast.access.AccessSession;
import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "server.port=0"
})
@AutoConfigureMockMvc
class AccessTokenFilterIntegrationTest extends CrewcastSpringTestBase {

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

    @BeforeEach
    void sharingOnWithFile() throws Exception {
        var settings = settingsStore.getSettings();
        settings.setRequirePrivateKey(true);
        settings.setAllowSameWifiAccess(true);
        settingsStore.update(settings);
        sharingState.setSharingActive(true);
        Path video = Files.createTempFile("crewcast-test-", ".mp4");
        video.toFile().deleteOnExit();
        shareRegistry.addFile(video);
    }

    @Test
    void loopbackTunnelHeaderWithValidTokenReturns200() throws Exception {
        String token = accessSession.getToken();
        mockMvc.perform(get("/")
                        .param("t", token)
                        .header("Cf-Connecting-Ip", "203.0.113.10")
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        }))
                .andExpect(status().isOk());
    }

    @Test
    void loopbackTunnelHeaderWithoutTokenReturns403() throws Exception {
        mockMvc.perform(get("/")
                        .header("Cf-Connecting-Ip", "203.0.113.10")
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        }))
                .andExpect(status().isForbidden());
    }

    @Test
    void loopbackTunnelHeaderWithWrongTokenReturns403() throws Exception {
        mockMvc.perform(get("/")
                        .param("t", "deadbeefdeadbeefdeadbeefdeadbeef")
                        .header("Cf-Connecting-Ip", "203.0.113.10")
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        }))
                .andExpect(status().isForbidden());
    }
}
