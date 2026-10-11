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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CaptionsBrowserTest extends CrewcastSpringTestBase {

    private static SubtitleFixture.Bundle fixture;

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
        assumeTrue(AudioFixIntegrationTest.ffmpegAvailable(), "ffmpeg not on PATH");
        if (fixture == null) {
            fixture = SubtitleFixture.create();
        }
        settingsStore.getSettings().setShares(new java.util.ArrayList<>());
        settingsStore.save();
        sharingState.setSharingActive(true);
        AppSettings s = settingsStore.getSettings();
        s.setRequirePrivateKey(true);
        s.setFixAudioForBrowsers(false);
        settingsStore.update(s);
        shareRegistry.addFile(fixture.sidecarVideo());
    }

    private static Browser launchBrowser(Playwright pw) {
        try {
            return pw.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome"));
        } catch (RuntimeException ex) {
            return pw.chromium().launch();
        }
    }

    @Test
    void selectingCaptionEnablesActiveTextTrack() throws Exception {
        String id = shareRegistry.listVideos().stream()
                .filter(v -> v.path().equals(fixture.sidecarVideo()))
                .findFirst()
                .orElseThrow()
                .id();
        String token = accessSession.getToken();
        String url = "http://127.0.0.1:" + port + "/watch/" + id + "?t=" + token;

        try (Playwright pw = Playwright.create();
             Browser browser = launchBrowser(pw);
             var context = browser.newContext();
             Page page = context.newPage()) {
            page.navigate(url);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            page.waitForSelector("#captions-select:not([hidden])", new Page.WaitForSelectorOptions().setTimeout(15000));
            page.selectOption("#captions-select", "0");

            page.waitForFunction("() => {"
                    + "const v = document.getElementById('video');"
                    + "if (!v || !v.textTracks) return false;"
                    + "for (let i = 0; i < v.textTracks.length; i++) {"
                    + "  const t = v.textTracks[i];"
                    + "  if (t.mode === 'showing' && t.cues && t.cues.length > 0) return true;"
                    + "}"
                    + "return false;"
                    + "}", null, new Page.WaitForFunctionOptions().setTimeout(20000));

            Boolean ok = (Boolean) page.evaluate("() => {"
                    + "const v = document.getElementById('video');"
                    + "for (let i = 0; i < v.textTracks.length; i++) {"
                    + "  const t = v.textTracks[i];"
                    + "  if (t.mode === 'showing' && t.cues && t.cues.length > 0) return true;"
                    + "}"
                    + "return false;"
                    + "}");
            assertTrue(Boolean.TRUE.equals(ok));
        }
    }
}
