package com.solocrew.crewcast.media;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.solocrew.crewcast.access.AccessSession;
import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.PrepareAudioMode;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WatchPlaybackBrowserTest extends CrewcastSpringTestBase {

    private static Path hevcFixture;
    private static Path h264Fixture;

    @LocalServerPort
    int port;

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
    static void fixtures(DynamicPropertyRegistry registry) throws Exception {
        assumeTrue(AudioFixIntegrationTest.ffmpegAvailable(), "ffmpeg not on PATH");
        hevcFixture = CoverArtFixture.create();
        h264Fixture = H264Fixture.create();
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
        s.setAllowSameWifiAccess(true);
        settingsStore.update(s);
        shareRegistry.addFile(hevcFixture);
        shareRegistry.addFile(h264Fixture);
        audioFixService.clearCache();
    }

    @AfterEach
    void restoreSettings() {
        AppSettings s = settingsStore.getSettings();
        s.setRequirePrivateKey(true);
        settingsStore.update(s);
    }

    private static Browser launchBrowser(Playwright pw) {
        try {
            return pw.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome"));
        } catch (RuntimeException ex) {
            return pw.chromium().launch();
        }
    }

    @Test
    void watchPageLoadsVideoAfterHevcAudioFixWithTunnelHeaders() throws Exception {
        String hevcId = shareRegistry.listVideos().stream()
                .filter(v -> v.path().equals(hevcFixture))
                .findFirst()
                .orElseThrow()
                .id();
        String token = accessSession.getToken();
        String base = "http://127.0.0.1:" + port;

        List<String> streamLog = new CopyOnWriteArrayList<>();
        List<String> statusLog = new CopyOnWriteArrayList<>();

        try (Playwright pw = Playwright.create()) {
            Browser browser = launchBrowser(pw);
            BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                    .setExtraHTTPHeaders(Map.of("Cf-Connecting-Ip", "203.0.113.10")));
            Page page = ctx.newPage();

            page.onResponse(resp -> {
                String url = resp.url();
                if (url.contains("/stream/")) {
                    streamLog.add(resp.status() + " " + url);
                }
                if (url.contains("/audio-status")) {
                    statusLog.add(resp.status() + " " + url);
                }
            });

            page.navigate(base + "/watch/" + hevcId + "?t=" + token);
            page.waitForSelector("#audio-picker-overlay:not([hidden])");
            page.locator(".audio-picker-btn").first().click();

            page.waitForFunction(
                    "() => { const v = document.getElementById('video'); "
                            + "return v && v.readyState >= 1 && Number.isFinite(v.duration) && v.duration > 0; }",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(180_000));

            Object snapshot = page.evaluate("() => { const v = document.getElementById('video'); "
                    + "return { readyState: v.readyState, duration: v.duration, currentSrc: v.currentSrc, "
                    + "error: v.error ? { code: v.error.code, message: v.error.message } : null }; }");

            browser.close();

            assertTrue(snapshot.toString().contains("duration"));
            assertTrue(statusLog.stream().anyMatch(l -> l.startsWith("200")), "audio-status polls: " + statusLog);
            assertTrue(streamLog.stream().anyMatch(l -> l.startsWith("206") || l.startsWith("200")),
                    "stream responses: " + streamLog);
        }
    }

    @Test
    void switchingAudioTrackUpdatesSrcAndPreservesTime() throws Exception {
        String hevcId = shareRegistry.listVideos().stream()
                .filter(v -> v.path().equals(hevcFixture))
                .findFirst()
                .orElseThrow()
                .id();
        String token = accessSession.getToken();
        List<String> statusLog = new CopyOnWriteArrayList<>();

        try (Playwright pw = Playwright.create()) {
            Browser browser = launchBrowser(pw);
            BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                    .setExtraHTTPHeaders(Map.of("Cf-Connecting-Ip", "203.0.113.10")));
            Page page = ctx.newPage();
            page.onResponse(resp -> {
                if (resp.url().contains("/audio-status")) {
                    statusLog.add(resp.status() + " " + resp.url());
                }
            });

            page.navigate("http://127.0.0.1:" + port + "/watch/" + hevcId + "?t=" + token);
            page.evaluate("() => localStorage.setItem('crewcast-audio-' + location.pathname.split('/').pop(), '0')");
            page.reload();
            page.waitForFunction(
                    "() => { const v = document.getElementById('video'); "
                            + "return v && v.readyState >= 1 && v.duration > 0; }",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(180_000));

            page.evaluate("() => { const v = document.getElementById('video'); v.currentTime = 0.3; }");
            double before = ((Number) page.evaluate("() => document.getElementById('video').currentTime")).doubleValue();

            page.selectOption("#audio-select", "1");

            page.waitForFunction(
                    "() => { const v = document.getElementById('video'); "
                            + "return v && v.currentSrc.includes('audio=1') && v.readyState >= 1 && v.duration > 0; }",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(180_000));

            double after = ((Number) page.evaluate("() => document.getElementById('video').currentTime")).doubleValue();
            String src = (String) page.evaluate("() => document.getElementById('video').currentSrc");

            browser.close();

            assertTrue(src.contains("audio=1"), "src: " + src);
            assertTrue(Math.abs(after - before) <= 2.0, "time before=" + before + " after=" + after);
            assertTrue(statusLog.stream().anyMatch(u -> u.contains("audio=1")), "status polls: " + statusLog);
        }
    }

    @Test
    void h264FixturePlaysWithoutConversion() throws Exception {
        String h264Id = shareRegistry.listVideos().stream()
                .filter(v -> v.path().equals(h264Fixture))
                .findFirst()
                .orElseThrow()
                .id();
        String token = accessSession.getToken();
        try (Playwright pw = Playwright.create()) {
            Browser browser = launchBrowser(pw);
            BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                    .setExtraHTTPHeaders(Map.of("Cf-Connecting-Ip", "203.0.113.10")));
            Page page = ctx.newPage();
            page.navigate("http://127.0.0.1:" + port + "/watch/" + h264Id + "?t=" + token);
            try {
                page.waitForFunction(
                        "() => { const v = document.getElementById('video'); "
                                + "return v && v.readyState >= 1 && v.duration > 0; }",
                        null,
                        new Page.WaitForFunctionOptions().setTimeout(30_000));
            } catch (RuntimeException ex) {
                Object snap = page.evaluate("() => { const v = document.getElementById('video'); "
                        + "return { readyState: v && v.readyState, duration: v && v.duration, "
                        + "currentSrc: v && v.currentSrc, error: v && v.error && v.error.code, "
                        + "errMsg: document.getElementById('video-error') && document.getElementById('video-error').textContent }; }");
                System.out.println("[debug] " + snap);
                throw ex;
            }
            browser.close();
        }
    }
}
