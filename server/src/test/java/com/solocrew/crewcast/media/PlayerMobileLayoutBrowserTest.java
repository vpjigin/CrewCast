package com.solocrew.crewcast.media;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.solocrew.crewcast.access.AccessSession;
import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@E2eFfmpegRequired
class PlayerMobileLayoutBrowserTest extends CrewcastSpringTestBase {

    private static Path coverArt;
    private static SubtitleFixture.Bundle subtitles;

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

    @BeforeEach
    void setup() throws Exception {
        if (coverArt == null) {
            coverArt = MediaTestFixtures.coverArt();
        }
        if (subtitles == null) {
            subtitles = SubtitleFixture.create();
        }
        settingsStore.getSettings().setShares(new java.util.ArrayList<>());
        settingsStore.save();
        sharingState.setSharingActive(true);
        AppSettings s = settingsStore.getSettings();
        s.setRequirePrivateKey(true);
        s.setFixAudioForBrowsers(true);
        settingsStore.update(s);
        shareRegistry.addFile(coverArt);
        shareRegistry.addFile(subtitles.sidecarVideo());
    }

    private static Browser launchBrowser(Playwright pw) {
        try {
            return pw.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome"));
        } catch (RuntimeException ex) {
            return pw.chromium().launch();
        }
    }

    @ParameterizedTest(name = "viewport {0}x{1}")
    @CsvSource({"390,844", "360,740", "320,568"})
    void topBarControlsDoNotOverlap(int width, int height) throws Exception {
        String coverId = shareRegistry.listVideos().stream()
                .filter(v -> v.path().equals(coverArt))
                .findFirst()
                .orElseThrow()
                .id();
        String subId = shareRegistry.listVideos().stream()
                .filter(v -> v.path().equals(subtitles.sidecarVideo()))
                .findFirst()
                .orElseThrow()
                .id();
        String token = accessSession.getToken();

        try (Playwright pw = Playwright.create();
             Browser browser = launchBrowser(pw);
             var context = browser.newContext();
             Page page = context.newPage()) {
            page.setViewportSize(width, height);

            String coverUrl = "http://127.0.0.1:" + port + "/watch/" + coverId + "?t=" + token;
            page.navigate(coverUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForSelector("#audio-select:not([hidden])", new Page.WaitForSelectorOptions().setTimeout(30000));
            assertLayoutOk(page, width, "cover-audio");
            page.screenshot(new Page.ScreenshotOptions().setPath(
                    Path.of("target", "player-mobile-cover-" + width + "x" + height + ".png")));

            String subUrl = "http://127.0.0.1:" + port + "/watch/" + subId + "?t=" + token;
            page.navigate(subUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForSelector("#captions-select:not([hidden])", new Page.WaitForSelectorOptions().setTimeout(15000));
            assertLayoutOk(page, width, "sidecar-captions");
            page.screenshot(new Page.ScreenshotOptions().setPath(
                    Path.of("target", "player-mobile-captions-" + width + "x" + height + ".png")));
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertLayoutOk(Page page, int viewportWidth, String label) {
        Object raw = page.evaluate("() => {"
                + "const ids = ['audio-select','captions-select','speed-select','btn-chapters','btn-fullscreen'];"
                + "const els = ids.map(id => document.getElementById(id)).filter(el => el && !el.hidden);"
                + "const boxes = els.map(el => {"
                + "  const r = el.getBoundingClientRect();"
                + "  return { id: el.id, left: r.left, top: r.top, right: r.right, bottom: r.bottom,"
                + "    w: r.width, h: r.height };"
                + "});"
                + "for (let i = 0; i < boxes.length; i++) {"
                + "  for (let j = i + 1; j < boxes.length; j++) {"
                + "    const a = boxes[i], b = boxes[j];"
                + "    const pad = 0.5;"
                + "    const overlap = a.right > b.left + pad && b.right > a.left + pad"
                + "      && a.bottom > b.top + pad && b.bottom > a.top + pad;"
                + "    if (overlap) return { ok: false, reason: 'overlap', a: a.id, b: b.id, boxes };"
                + "  }"
                + "}"
                + "const docW = document.documentElement.clientWidth;"
                + "for (const b of boxes) {"
                + "  if (b.right > docW + 0.5) return { ok: false, reason: 'overflow', id: b.id, right: b.right, docW };"
                + "  if (b.h < 40) return { ok: false, reason: 'touch', id: b.id, h: b.h };"
                + "}"
                + "return { ok: true, boxes, docW };"
                + "}");
        assertTrue(raw instanceof java.util.Map, label + ": unexpected evaluate result");
        java.util.Map<String, Object> result = (java.util.Map<String, Object>) raw;
        assertTrue(Boolean.TRUE.equals(result.get("ok")),
                label + " @" + viewportWidth + "px: " + result);
    }
}
