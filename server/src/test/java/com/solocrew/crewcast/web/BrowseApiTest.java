package com.solocrew.crewcast.web;

import com.solocrew.crewcast.access.AccessSession;
import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
class BrowseApiTest extends CrewcastSpringTestBase {

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

    Path shareDir;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        settingsStore.getSettings().setShares(new java.util.ArrayList<>());
        settingsStore.save();
        var settings = settingsStore.getSettings();
        settings.setRequirePrivateKey(true);
        settings.setAllowSameWifiAccess(true);
        settingsStore.update(settings);
        sharingState.setSharingActive(true);
        token = accessSession.getToken();

        shareDir = Files.createTempDirectory("crewcast-browse-");
        shareDir.toFile().deleteOnExit();
        Files.writeString(shareDir.resolve("notes.pdf"), "%PDF", StandardCharsets.UTF_8);
        Path nested = Files.createDirectories(shareDir.resolve("inner"));
        Files.writeString(nested.resolve("deep.txt"), "deep", StandardCharsets.UTF_8);
        shareRegistry.addFolder(shareDir);
    }

    @Test
    void topLevelListsSharedFolderNotInnerFiles() throws Exception {
        String folderId = ShareRegistry.idForPath(shareDir);
        mockMvc.perform(get("/api/browse").param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders", hasSize(1)))
                .andExpect(jsonPath("$.folders[0].id", org.hamcrest.Matchers.is(folderId)))
                .andExpect(jsonPath("$.folders[0].name", org.hamcrest.Matchers.is(shareDir.getFileName().toString())))
                .andExpect(jsonPath("$.files", hasSize(0)))
                .andExpect(jsonPath("$.headerSummary", containsString("folder")))
                .andExpect(jsonPath("$.files[*].name", not(hasItem("notes.pdf"))));
    }

    @Test
    void nestedFolderListsSubfoldersBeforeFiles() throws Exception {
        Path inner = shareDir.resolve("inner");
        Path sub = Files.createDirectories(inner.resolve("sub"));
        Files.writeString(sub.resolve("z-last.txt"), "z", StandardCharsets.UTF_8);
        Files.writeString(inner.resolve("a-first.txt"), "a", StandardCharsets.UTF_8);
        String innerId = ShareRegistry.idForPath(inner);

        mockMvc.perform(get("/api/browse").param("folder", innerId).param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders[0].name", org.hamcrest.Matchers.is("sub")))
                .andExpect(jsonPath("$.files[0].name", org.hamcrest.Matchers.is("a-first.txt")))
                .andExpect(jsonPath("$.files[1].name", org.hamcrest.Matchers.is("deep.txt")));
    }

    @Test
    void itemCountIsImmediateChildrenOnly() throws Exception {
        Path inner = shareDir.resolve("inner");
        Files.createDirectories(inner.resolve("child"));
        String folderId = ShareRegistry.idForPath(shareDir);
        mockMvc.perform(get("/api/browse").param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders[0].itemCount", org.hamcrest.Matchers.is(2)));
    }

    @Test
    void unknownFolderIdReturns404() throws Exception {
        mockMvc.perform(get("/api/browse").param("folder", "not-a-real-id").param("t", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void hiddenEntriesAbsentAtEveryLevel() throws Exception {
        Files.writeString(shareDir.resolve(".secret"), "nope", StandardCharsets.UTF_8);
        Path inner = shareDir.resolve("inner");
        Files.writeString(inner.resolve(".hidden"), "nope", StandardCharsets.UTF_8);
        String innerId = ShareRegistry.idForPath(inner);

        mockMvc.perform(get("/api/browse").param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders[*].name", not(hasItem(".secret"))));

        mockMvc.perform(get("/api/browse").param("folder", innerId).param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[*].name", not(hasItem(".hidden"))));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void symlinkOutsideShareNotListed() throws Exception {
        Path outside = Files.createTempFile("outside-", ".txt");
        outside.toFile().deleteOnExit();
        Files.writeString(outside, "secret", StandardCharsets.UTF_8);
        Path link = shareDir.resolve("escape-link.txt");
        Files.createSymbolicLink(link, outside);
        mockMvc.perform(get("/api/browse").param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[*].name", not(hasItem("escape-link.txt"))));
    }

    @Test
    void responseDoesNotContainAbsolutePaths() throws Exception {
        String innerId = ShareRegistry.idForPath(shareDir.resolve("inner"));
        MvcResult result = mockMvc.perform(get("/api/browse").param("folder", innerId).param("t", token))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains(shareDir.toString()));
        assertFalse(body.contains(System.getProperty("user.home")));
    }

    @Test
    void topLevelIncludesExplicitFileShare() throws Exception {
        Path sheet = Files.createTempFile("crewcast-explicit-", ".txt");
        sheet.toFile().deleteOnExit();
        Files.writeString(sheet, "explicit", StandardCharsets.UTF_8);
        shareRegistry.addFile(sheet);
        mockMvc.perform(get("/api/browse").param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[*].name", hasItem(sheet.getFileName().toString())));
    }

    @Test
    void filesSortedInNaturalOrder() throws Exception {
        Path sortDir = Files.createDirectories(shareDir.resolve("sort-only"));
        Files.writeString(sortDir.resolve("file10.txt"), "a", StandardCharsets.UTF_8);
        Files.writeString(sortDir.resolve("file2.txt"), "b", StandardCharsets.UTF_8);
        String sortDirId = ShareRegistry.idForPath(sortDir);

        mockMvc.perform(get("/api/browse").param("folder", sortDirId).param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].name", org.hamcrest.Matchers.is("file2.txt")))
                .andExpect(jsonPath("$.files[1].name", org.hamcrest.Matchers.is("file10.txt")));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void symlinkDirectoryOutsideShareIsNotBrowsable() throws Exception {
        Path outsideDir = Files.createTempDirectory("crewcast-outside-dir-");
        outsideDir.toFile().deleteOnExit();
        Path secret = outsideDir.resolve("secret.txt");
        Files.writeString(secret, "secret", StandardCharsets.UTF_8);
        Path linkDir = shareDir.resolve("outside-dir");
        Files.createSymbolicLink(linkDir, outsideDir);
        String linkDirId = ShareRegistry.idForPath(linkDir);
        String secretId = ShareRegistry.idForPath(secret);
        String shareFolderId = ShareRegistry.idForPath(shareDir);

        mockMvc.perform(get("/api/browse").param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders[*].name", not(hasItem("outside-dir"))));

        mockMvc.perform(get("/api/browse").param("folder", linkDirId).param("t", token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/browse").param("folder", shareFolderId).param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[*].name", not(hasItem("secret.txt"))))
                .andExpect(jsonPath("$.folders[*].name", not(hasItem("outside-dir"))));

        mockMvc.perform(get("/file/" + secretId).param("download", "true").param("t", token))
                .andExpect(status().isNotFound());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void symlinkDirectoryToHomeOrRootIsRejected() throws Exception {
        Path home = Path.of(System.getProperty("user.home"));
        Path homeLeak = home.resolve("crewcast-browse-leak-" + UUID.randomUUID() + ".txt");
        Files.writeString(homeLeak, "leak", StandardCharsets.UTF_8);
        homeLeak.toFile().deleteOnExit();
        String leakId = ShareRegistry.idForPath(homeLeak);

        Path homeLink = shareDir.resolve("home-link");
        Files.createSymbolicLink(homeLink, home);
        String homeLinkId = ShareRegistry.idForPath(homeLink);

        mockMvc.perform(get("/api/browse").param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders[*].name", not(hasItem("home-link"))));

        mockMvc.perform(get("/api/browse").param("folder", homeLinkId).param("t", token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/file/" + leakId).param("download", "true").param("t", token))
                .andExpect(status().isNotFound());

        Path rootLink = shareDir.resolve("root-link");
        Files.createSymbolicLink(rootLink, Path.of("/"));
        String rootLinkId = ShareRegistry.idForPath(rootLink);
        mockMvc.perform(get("/api/browse").param("folder", rootLinkId).param("t", token))
                .andExpect(status().isNotFound());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void symlinkDirectoryLoopDoesNotHangBrowseOrItemCount() throws Exception {
        Path loop = Files.createDirectories(shareDir.resolve("loopnest"));
        Files.createSymbolicLink(loop.resolve("self"), loop);
        String loopId = ShareRegistry.idForPath(loop);

        assertTimeout(Duration.ofSeconds(5), () -> {
            try {
                mockMvc.perform(get("/api/browse").param("t", token))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.folders[0].itemCount", org.hamcrest.Matchers.notNullValue()));
                mockMvc.perform(get("/api/browse").param("folder", loopId).param("t", token))
                        .andExpect(status().isOk());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void breadcrumbUsesNamesOnly() throws Exception {
        String innerId = ShareRegistry.idForPath(shareDir.resolve("inner"));
        MvcResult result = mockMvc.perform(get("/api/browse").param("folder", innerId).param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breadcrumb[0].name", org.hamcrest.Matchers.is("Shared")))
                .andExpect(jsonPath("$.breadcrumb[1].name", org.hamcrest.Matchers.is(shareDir.getFileName().toString())))
                .andExpect(jsonPath("$.breadcrumb[2].name", org.hamcrest.Matchers.is("inner")))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"Shared\""));
    }
}
