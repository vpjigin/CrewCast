package com.solocrew.crewcast.media;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.solocrew.crewcast.access.AccessSession;
import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.PrepareAudioMode;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@E2eFfmpegRequired
class OnDemandAudioBrowserTest extends CrewcastSpringTestBase {

    private static Path fixture;

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ShareRegistry shareRegistry;
    @Autowired
    SharingState sharingState;
    @Autowired
    SettingsStore settingsStore;
    @Autowired
    AccessSession accessSession;
    @Autowired
    AudioFixService audioFixService;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        System.setProperty("crewcast.audio.idle-cancel-ms", "3000");
        if (!FfmpegTestSupport.ffmpegAndFfprobeAvailable()) {
            return;
        }
        if (fixture == null) {
            fixture = MediaTestFixtures.coverArt();
        }
    }

    @BeforeEach
    void sharingOn() throws Exception {
        settingsStore.getSettings().setShares(new java.util.ArrayList<>());
        settingsStore.save();
        sharingState.setSharingActive(true);
        AppSettings s = settingsStore.getSettings();
        s.setFixAudioForBrowsers(true);
        s.setPrepareAudioInAdvance(PrepareAudioMode.OFF);
        s.setRequirePrivateKey(true);
        settingsStore.update(s);
        shareRegistry.addFile(fixture);
        audioFixService.clearCache();
    }

    private static Browser launchBrowser(Playwright pw) {
        try {
            return pw.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome"));
        } catch (RuntimeException ex) {
            return pw.chromium().launch();
        }
    }

    @Test
    void pickerThenPrepareOnlyChosenTrack() throws Exception {
        String videoId = shareRegistry.listVideos().getFirst().id();
        String token = accessSession.getToken();

        mockMvc.perform(get("/api/videos/" + videoId + "/audio-status?audio=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_PREPARED"));

        try (Playwright pw = Playwright.create()) {
            Browser browser = launchBrowser(pw);
            BrowserContext ctx = browser.newContext();
            Page page = ctx.newPage();

            page.navigate("http://127.0.0.1:" + port + "/watch/" + videoId + "?t=" + token);
            page.waitForSelector("#audio-picker-overlay:not([hidden])");

            mockMvc.perform(get("/api/videos/" + videoId + "/audio-status?audio=1"))
                    .andExpect(jsonPath("$.status").value("NOT_PREPARED"));

            page.locator(".audio-picker-btn").nth(1).click();
            page.waitForFunction(
                    "() => { const v = document.getElementById('video'); "
                            + "return v && v.currentSrc.includes('audio=1') && v.readyState >= 1 && v.duration > 0; }",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(180_000));

            browser.close();
        }

        assertTrue(audioFixService.isTrackReady(fixture, 1));
        assertFalse(audioFixService.isTrackReady(fixture, 0));
    }

    @Test
    void savedChoiceSkipsPickerOnReopen() throws Exception {
        String videoId = shareRegistry.listVideos().getFirst().id();
        String token = accessSession.getToken();

        try (Playwright pw = Playwright.create()) {
            Browser browser = launchBrowser(pw);
            BrowserContext ctx = browser.newContext();
            Page page = ctx.newPage();
            page.navigate("http://127.0.0.1:" + port + "/watch/" + videoId + "?t=" + token);
            page.waitForSelector("#audio-picker-overlay:not([hidden])");
            page.locator(".audio-picker-btn").first().click();
            page.waitForFunction(
                    "() => document.getElementById('video').readyState >= 1 && document.getElementById('video').duration > 0",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(180_000));

            Page page2 = ctx.newPage();
            page2.navigate("http://127.0.0.1:" + port + "/watch/" + videoId + "?t=" + token);
            page2.waitForFunction(
                    "() => document.getElementById('audio-picker-overlay').hidden",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(10_000));
            browser.close();
        }
    }

    @Test
    void getStreamAndStatusDoNotImplicitlyPrepare() throws Exception {
        String videoId = shareRegistry.listVideos().getFirst().id();

        mockMvc.perform(get("/api/videos/" + videoId + "/audio-status?audio=1"))
                .andExpect(jsonPath("$.status").value("NOT_PREPARED"));

        mockMvc.perform(get("/stream/" + videoId + "?audio=1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("NOT_PREPARED"));

        assertFalse(audioFixService.isTrackReady(fixture, 1));
    }
}
