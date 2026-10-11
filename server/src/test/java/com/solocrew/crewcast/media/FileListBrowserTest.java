package com.solocrew.crewcast.media;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("e2e")
class FileListBrowserTest extends CrewcastSpringTestBase {

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
    void sharingOn() throws Exception {
        settingsStore.getSettings().setShares(new java.util.ArrayList<>());
        settingsStore.save();
        var s = settingsStore.getSettings();
        s.setRequirePrivateKey(true);
        s.setAllowSameWifiAccess(true);
        settingsStore.update(s);
        sharingState.setSharingActive(true);
    }

    @Test
    void homePageOpensFilePopup() throws Exception {
        Path dir = Files.createTempDirectory("crewcast-ui-");
        dir.toFile().deleteOnExit();
        Files.writeString(dir.resolve("clip.mp4"), "fake");
        Files.writeString(dir.resolve("doc.pdf"), "%PDF");
        Files.writeString(dir.resolve("archive.zip"), "PK");
        shareRegistry.addFolder(dir);
        String token = accessSession.getToken();
        String base = "http://127.0.0.1:" + port;

        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch();
            BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                    .setExtraHTTPHeaders(Map.of("Cf-Connecting-Ip", "203.0.113.10")));
            Page page = ctx.newPage();
            page.navigate(base + "/?t=" + token);
            page.waitForSelector(".file-card");

            page.locator(".file-card").filter(new Locator.FilterOptions().setHasText("clip.mp4")).click();
            page.locator("#modal").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            assertTrue(page.locator("#btn-view").isVisible());
            assertTrue(page.locator("#btn-download").isVisible());

            page.keyboard().press("Escape");
            page.locator("#modal").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
            page.waitForFunction("() => document.activeElement && document.activeElement.classList.contains('file-card')");

            page.locator(".file-card").filter(new Locator.FilterOptions().setHasText("doc.pdf")).click();
            page.locator("#modal").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            assertTrue(page.locator("#modal-no-preview").isVisible());
            assertTrue(page.locator("#btn-download").isVisible());
            assertEquals(0, page.locator("#modal").getByRole(AriaRole.BUTTON,
                    new Locator.GetByRoleOptions().setName("View")).count());

            page.keyboard().press("Escape");
            page.locator("#modal").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));

            page.locator(".file-card").filter(new Locator.FilterOptions().setHasText("archive.zip")).click();
            page.locator("#modal").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            assertEquals(0, page.locator("#modal").getByRole(AriaRole.BUTTON,
                    new Locator.GetByRoleOptions().setName("View")).count());
            page.locator("#modal-close").click();
            page.locator("#modal").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));

            browser.close();
        }
    }
}
