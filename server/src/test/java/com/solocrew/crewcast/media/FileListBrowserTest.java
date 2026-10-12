package com.solocrew.crewcast.media;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.solocrew.crewcast.access.AccessSession;
import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("e2e")
@E2eFfmpegRequired
class FileListBrowserTest extends CrewcastSpringTestBase {

    private static Path h264Fixture;
    private static Path silentH264Fixture;

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

    @DynamicPropertySource
    static void fixtures(DynamicPropertyRegistry registry) throws Exception {
        if (!FfmpegTestSupport.ffmpegAndFfprobeAvailable()) {
            return;
        }
        if (h264Fixture == null) {
            h264Fixture = MediaTestFixtures.h264();
        }
        if (silentH264Fixture == null) {
            silentH264Fixture = MediaTestFixtures.silentH264();
        }
    }

    private static Browser launchBrowser(Playwright pw) {
        try {
            return pw.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome"));
        } catch (RuntimeException ex) {
            return pw.chromium().launch();
        }
    }

    @BeforeEach
    void sharingOn() throws Exception {
        settingsStore.getSettings().setShares(new java.util.ArrayList<>());
        settingsStore.save();
        var s = settingsStore.getSettings();
        s.setRequirePrivateKey(true);
        s.setAllowSameWifiAccess(true);
        s.setFixAudioForBrowsers(true);
        settingsStore.update(s);
        sharingState.setSharingActive(true);
    }

    @Test
    void homePageOpensFilePopup() throws Exception {
        Path dir = Files.createTempDirectory("crewcast-ui-");
        dir.toFile().deleteOnExit();
        Files.copy(h264Fixture, dir.resolve("Sample.mp4"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(dir.resolve("photo.jpg"), "not-a-real-jpeg");
        Files.writeString(dir.resolve("doc.pdf"), "%PDF");
        Files.writeString(dir.resolve("archive.zip"), "PK");
        shareRegistry.addFolder(dir);
        String videoId = shareRegistry.listVideos().stream()
                .filter(v -> v.displayName().equals("Sample.mp4"))
                .findFirst()
                .orElseThrow()
                .id();
        String token = accessSession.getToken();
        String base = "http://127.0.0.1:" + port;

        try (Playwright pw = Playwright.create()) {
            Browser browser = launchBrowser(pw);
            BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                    .setExtraHTTPHeaders(Map.of("Cf-Connecting-Ip", "203.0.113.10")));
            Page page = ctx.newPage();
            page.navigate(base + "/?t=" + token);
            page.waitForSelector(".folder-card");
            assertEquals(0, page.locator(".file-card:not(.folder-card)").count());

            page.locator(".folder-card").first().click();
            page.waitForSelector(".file-card:not(.folder-card)");
            page.waitForSelector("#btn-download-all:not([hidden])");
            assertTrue(page.locator("#btn-download-all").isVisible());
            page.locator(".file-card:not(.folder-card)").filter(new Locator.FilterOptions().setHasText("Sample.mp4")).click();
            page.locator("#modal").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            page.locator("#modal").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Watch")).click();
            page.waitForURL("**/watch/" + videoId + "**");
            assertTrue(page.url().contains("/watch/" + videoId));
            assertTrue(page.url().contains("t="));

            page.locator("#back-to-files").click();
            page.waitForFunction("() => new URLSearchParams(location.search).has('folder')");
            page.locator(".file-card:not(.folder-card)").filter(new Locator.FilterOptions().setHasText("Sample.mp4")).waitFor();

            page.locator(".file-card:not(.folder-card)").filter(new Locator.FilterOptions().setHasText("photo.jpg")).click();
            page.locator("#modal").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            page.locator("#btn-view").click();
            page.locator("#image-viewer").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            page.keyboard().press("Escape");
            page.locator("#image-viewer").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));

            page.locator(".file-card:not(.folder-card)").filter(new Locator.FilterOptions().setHasText("doc.pdf")).click();
            page.locator("#modal").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            assertEquals(0, page.locator("#modal").getByRole(AriaRole.BUTTON,
                    new Locator.GetByRoleOptions().setName("View")).count());
            assertEquals(0, page.locator("#modal").getByRole(AriaRole.BUTTON,
                    new Locator.GetByRoleOptions().setName("Watch")).count());

            page.keyboard().press("Escape");
            page.locator("#modal").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));

            page.locator(".file-card:not(.folder-card)").filter(new Locator.FilterOptions().setHasText("archive.zip")).click();
            page.locator("#modal").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            assertEquals(0, page.locator("#modal").getByRole(AriaRole.BUTTON,
                    new Locator.GetByRoleOptions().setName("View")).count());
            assertEquals(0, page.locator("#modal").getByRole(AriaRole.BUTTON,
                    new Locator.GetByRoleOptions().setName("Watch")).count());
            page.locator("#modal-close").click();
            page.locator("#modal").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));

            browser.close();
        }
    }

    @Test
    void folderBrowseBreadcrumbAndBack() throws Exception {
        Path dir = Files.createTempDirectory("crewcast-nest-");
        dir.toFile().deleteOnExit();
        Path sub = Files.createDirectories(dir.resolve("sub-folder"));
        Files.copy(h264Fixture, sub.resolve("nested.mp4"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(dir.resolve("root.txt"), "root");
        shareRegistry.addFolder(dir);
        String token = accessSession.getToken();
        String base = "http://127.0.0.1:" + port;

        try (Playwright pw = Playwright.create()) {
            Browser browser = launchBrowser(pw);
            BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                    .setExtraHTTPHeaders(Map.of("Cf-Connecting-Ip", "203.0.113.10")));
            Page page = ctx.newPage();
            page.navigate(base + "/?t=" + token);
            page.waitForSelector(".folder-card");
            assertEquals(0, page.locator(".file-card:not(.folder-card)").count());

            page.locator(".folder-card").first().click();
            page.waitForFunction("() => new URLSearchParams(location.search).has('folder')");
            page.locator(".folder-card").filter(new Locator.FilterOptions().setHasText("sub-folder")).waitFor();

            page.locator(".folder-card").filter(new Locator.FilterOptions().setHasText("sub-folder")).click();
            page.locator(".file-card:not(.folder-card)").filter(new Locator.FilterOptions().setHasText("nested.mp4")).waitFor();
            assertTrue(page.locator(".breadcrumb").getByText("sub-folder").isVisible());

            page.locator(".breadcrumb-link").filter(new Locator.FilterOptions().setHasText("Shared")).click();
            page.waitForFunction("() => !new URLSearchParams(location.search).has('folder')");
            page.locator(".folder-card").first().waitFor();

            page.goBack();
            page.waitForFunction("() => new URLSearchParams(location.search).has('folder')");
            assertTrue(page.locator(".file-card:not(.folder-card)").filter(new Locator.FilterOptions().setHasText("nested.mp4")).isVisible());

            page.locator(".file-card:not(.folder-card)").filter(new Locator.FilterOptions().setHasText("nested.mp4")).click();
            page.locator("#modal").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            page.locator("#modal").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Watch")).click();
            page.waitForURL("**/watch/**");
            assertTrue(page.url().contains("t="));
            assertTrue(page.url().contains("folder="));

            page.locator("#back-to-files").click();
            page.waitForFunction("() => new URLSearchParams(location.search).has('folder')");
            page.locator(".file-card:not(.folder-card)").filter(new Locator.FilterOptions().setHasText("nested.mp4")).waitFor();
            assertTrue(page.locator(".breadcrumb").getByText("sub-folder").isVisible());

            browser.close();
        }
    }

    @Test
    void silentVideoPlaysOnWatchPage() throws Exception {
        Path dir = Files.createTempDirectory("crewcast-silent-ui-");
        dir.toFile().deleteOnExit();
        Files.copy(silentH264Fixture, dir.resolve("silent.mp4"), StandardCopyOption.REPLACE_EXISTING);
        shareRegistry.addFolder(dir);
        String silentId = shareRegistry.listVideos().stream()
                .filter(v -> v.displayName().equals("silent.mp4"))
                .findFirst()
                .orElseThrow()
                .id();
        String token = accessSession.getToken();
        String base = "http://127.0.0.1:" + port;

        try (Playwright pw = Playwright.create()) {
            Browser browser = launchBrowser(pw);
            BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                    .setExtraHTTPHeaders(Map.of("Cf-Connecting-Ip", "203.0.113.10")));
            Page page = ctx.newPage();
            String watchUrl = base + "/watch/" + silentId + "?t=" + token;
            Response streamResp = page.waitForResponse(
                    r -> r.url().contains("/stream/" + silentId)
                            && (r.status() == 200 || r.status() == 206),
                    () -> page.navigate(watchUrl));
            assertTrue(streamResp.status() == 200 || streamResp.status() == 206);
            page.waitForSelector("#audio-none");
            assertTrue(page.locator("#audio-none").isVisible());
            page.evaluate("() => { const v = document.getElementById('video'); if (v) v.play().catch(function(){}); }");
            page.waitForFunction(
                    "() => { const v = document.getElementById('video'); "
                            + "const err = document.getElementById('video-error'); "
                            + "return v && v.readyState >= 2 && Number.isFinite(v.duration) && v.duration > 0 "
                            + "&& (!err || !err.textContent); }",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(45_000));
            assertTrue(page.locator("#video-error").isHidden());
            browser.close();
        }
    }
}
