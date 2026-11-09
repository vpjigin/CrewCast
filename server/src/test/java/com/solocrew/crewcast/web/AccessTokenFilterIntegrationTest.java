package com.solocrew.crewcast.web;

import com.solocrew.crewcast.access.AccessSession;
import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.facade.SharingService;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

    @Autowired
    SharingService sharingService;

    @BeforeEach
    void sharingOnWithFile() throws Exception {
        applyAccessSettings(true, true);
        sharingState.setSharingActive(true);
        Path video = Files.createTempFile("crewcast-test-", ".mp4");
        video.toFile().deleteOnExit();
        shareRegistry.addFile(video);
    }

    @AfterEach
    void restoreAccessSettings() {
        // Shared Spring context — leave a known baseline for other classes.
        applyAccessSettings(true, true);
    }

    private void applyAccessSettings(boolean requirePrivateKey, boolean allowSameWifiAccess) {
        var settings = settingsStore.getSettings();
        settings.setRequirePrivateKey(requirePrivateKey);
        settings.setAllowSameWifiAccess(allowSameWifiAccess);
        settingsStore.update(settings);
    }

    private static RequestPostProcessor remoteAddr(String addr) {
        return req -> {
            req.setRemoteAddr(addr);
            return req;
        };
    }

    @Test
    void loopbackTunnelHeaderWithValidTokenReturns200() throws Exception {
        String token = accessSession.getToken();
        mockMvc.perform(get("/")
                        .param("t", token)
                        .header("Cf-Connecting-Ip", "203.0.113.10")
                        .with(remoteAddr("127.0.0.1")))
                .andExpect(status().isOk());
    }

    @Test
    void loopbackTunnelHeaderWithoutTokenReturns403() throws Exception {
        mockMvc.perform(get("/")
                        .header("Cf-Connecting-Ip", "203.0.113.10")
                        .with(remoteAddr("127.0.0.1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void loopbackTunnelHeaderWithWrongTokenReturns403() throws Exception {
        mockMvc.perform(get("/")
                        .param("t", "deadbeefdeadbeefdeadbeefdeadbeef")
                        .header("Cf-Connecting-Ip", "203.0.113.10")
                        .with(remoteAddr("127.0.0.1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void spoofedXffFromPublicClientIsRejected() throws Exception {
        applyAccessSettings(true, true);
        mockMvc.perform(get("/")
                        .header("X-Forwarded-For", "192.168.1.5")
                        .with(remoteAddr("203.0.113.5")))
                .andExpect(status().isForbidden());
    }

    @Test
    void spoofedCfHeaderFromPublicClientIsRejected() throws Exception {
        applyAccessSettings(true, true);
        mockMvc.perform(get("/")
                        .header("Cf-Connecting-Ip", "192.168.1.5")
                        .with(remoteAddr("203.0.113.5")))
                .andExpect(status().isForbidden());
    }

    @Test
    void lanWithoutTokenDependsOnSameWifiSetting() throws Exception {
        applyAccessSettings(true, true);
        mockMvc.perform(get("/").with(remoteAddr("192.168.1.20")))
                .andExpect(status().isOk());

        applyAccessSettings(true, false);
        mockMvc.perform(get("/").with(remoteAddr("192.168.1.20")))
                .andExpect(status().isForbidden());
    }

    @Test
    void loopbackWithoutTokenAndSameWifiOffReturns403() throws Exception {
        applyAccessSettings(true, false);
        mockMvc.perform(get("/").with(remoteAddr("127.0.0.1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void lanWithValidTokenAndSameWifiOffReturns200() throws Exception {
        applyAccessSettings(true, false);
        mockMvc.perform(get("/")
                        .param("t", accessSession.getToken())
                        .with(remoteAddr("192.168.1.20")))
                .andExpect(status().isOk());
    }

    @Test
    void validCookieGrantsAccessUntilTokenRotated() throws Exception {
        applyAccessSettings(true, false);
        String token = accessSession.getToken();
        mockMvc.perform(get("/")
                        .cookie(new Cookie(AccessSession.COOKIE_NAME, token))
                        .with(remoteAddr("203.0.113.5")))
                .andExpect(status().isOk());

        accessSession.regenerateToken();
        mockMvc.perform(get("/")
                        .cookie(new Cookie(AccessSession.COOKIE_NAME, token))
                        .with(remoteAddr("203.0.113.5")))
                .andExpect(status().isForbidden());
    }

    @Test
    void keyOffAllowsTunnelWithoutToken() throws Exception {
        applyAccessSettings(false, false);
        mockMvc.perform(get("/")
                        .header("Cf-Connecting-Ip", "203.0.113.10")
                        .with(remoteAddr("127.0.0.1")))
                .andExpect(status().isOk());
    }

    @Test
    void currentLinksIncludeTokenOnlyWhenKeyRequired() {
        assertTrue(sharingService.isServerReady(), "need a bound port for lanLink");
        applyAccessSettings(true, false);
        String token = accessSession.getToken();
        String lanOn = sharingService.currentLinks().lanLink();
        assertFalse(lanOn.isBlank());
        assertTrue(lanOn.endsWith("/?t=" + token));

        applyAccessSettings(false, false);
        String lanOff = sharingService.currentLinks().lanLink();
        assertFalse(lanOff.isBlank());
        assertTrue(lanOff.endsWith("/"));
        assertFalse(lanOff.contains("?t="));
    }
}
