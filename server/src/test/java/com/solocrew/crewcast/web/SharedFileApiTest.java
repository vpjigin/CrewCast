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
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
class SharedFileApiTest extends CrewcastSpringTestBase {

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
    Path pdfFile;
    String pdfId;

    @BeforeEach
    void setUp() throws Exception {
        settingsStore.getSettings().setShares(new java.util.ArrayList<>());
        settingsStore.save();
        var settings = settingsStore.getSettings();
        settings.setRequirePrivateKey(true);
        settings.setAllowSameWifiAccess(true);
        settingsStore.update(settings);
        sharingState.setSharingActive(true);

        shareDir = Files.createTempDirectory("crewcast-share-");
        shareDir.toFile().deleteOnExit();
        pdfFile = shareDir.resolve("notes.pdf");
        Files.writeString(pdfFile, "%PDF-1.4 test", StandardCharsets.UTF_8);
        shareRegistry.addFolder(shareDir);
        pdfId = shareRegistry.listSharedFiles().stream()
                .filter(f -> f.name().equals("notes.pdf"))
                .findFirst()
                .orElseThrow()
                .id();
    }

    @Test
    void listIncludesNonVideoFilesWithKindAndSize() throws Exception {
        String token = accessSession.getToken();
        mockMvc.perform(get("/api/files").param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.files[*].name", hasItem("notes.pdf")))
                .andExpect(jsonPath("$.files[?(@.name=='notes.pdf')].kind", hasItem("Document")))
                .andExpect(jsonPath("$.files[?(@.name=='notes.pdf')].sizeBytes", hasItem(greaterThan(0))));
    }

    @Test
    void downloadSetsAttachmentAndFilename() throws Exception {
        String token = accessSession.getToken();
        mockMvc.perform(get("/file/" + pdfId).param("download", "true").param("t", token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("notes.pdf")));
    }

    @Test
    void downloadUnicodeFilenameUsesRfc5987() throws Exception {
        Path unicode = shareDir.resolve("café résumé.pdf");
        Files.writeString(unicode, "%PDF", StandardCharsets.UTF_8);
        String id = shareRegistry.listSharedFiles().stream()
                .filter(f -> f.name().equals("café résumé.pdf"))
                .findFirst()
                .orElseThrow()
                .id();
        String token = accessSession.getToken();
        mockMvc.perform(get("/file/" + id).param("download", "1").param("t", token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.anyOf(
                                org.hamcrest.Matchers.containsString("filename*=UTF-8''"),
                                org.hamcrest.Matchers.containsString("utf-8''"))));
    }

    @Test
    void downloadWithoutTokenWhenRequiredReturns403() throws Exception {
        mockMvc.perform(get("/file/" + pdfId).param("download", "true")
                        .header("Cf-Connecting-Ip", "203.0.113.10")
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        }))
                .andExpect(status().isForbidden());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void symlinkOutsideShareIsRejected() throws Exception {
        Path outside = Files.createTempFile("outside-", ".txt");
        outside.toFile().deleteOnExit();
        Files.writeString(outside, "secret", StandardCharsets.UTF_8);
        Path link = shareDir.resolve("escape-link.txt");
        Files.createSymbolicLink(link, outside);
        String linkId = ShareRegistry.idForPath(link);
        String token = accessSession.getToken();
        mockMvc.perform(get("/file/" + linkId).param("download", "true").param("t", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownFileIdReturns404() throws Exception {
        String token = accessSession.getToken();
        mockMvc.perform(get("/file/deadbeef").param("download", "true").param("t", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void hiddenFileInFolderNotListedOrDownloadable() throws Exception {
        Path hidden = shareDir.resolve(".secret");
        Files.writeString(hidden, "nope", StandardCharsets.UTF_8);
        String hiddenId = ShareRegistry.idForPath(hidden);
        String token = accessSession.getToken();
        mockMvc.perform(get("/api/files").param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[*].name", org.hamcrest.Matchers.not(hasItem(".secret"))));
        mockMvc.perform(get("/file/" + hiddenId).param("download", "true").param("t", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void gitAndNodeModulesSkipped() throws Exception {
        Files.createDirectories(shareDir.resolve(".git"));
        Files.writeString(shareDir.resolve(".git/config"), "git", StandardCharsets.UTF_8);
        Files.createDirectories(shareDir.resolve("node_modules/pkg"));
        Files.writeString(shareDir.resolve("node_modules/pkg/a.txt"), "mod", StandardCharsets.UTF_8);
        String token = accessSession.getToken();
        mockMvc.perform(get("/api/files").param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[*].name", hasItem("notes.pdf")))
                .andExpect(jsonPath("$.files[*].name", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("config"))))
                .andExpect(jsonPath("$.files[*].name", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("a.txt"))));
    }

    @Test
    void explicitSingleFileShareOfNonVideoWorks() throws Exception {
        Path sheet = Files.createTempFile("crewcast-explicit-", ".txt");
        sheet.toFile().deleteOnExit();
        Files.writeString(sheet, "explicit", StandardCharsets.UTF_8);
        shareRegistry.addFile(sheet);
        String id = shareRegistry.listSharedFiles().stream()
                .filter(f -> f.name().equals(sheet.getFileName().toString()))
                .findFirst()
                .orElseThrow()
                .id();
        String token = accessSession.getToken();
        mockMvc.perform(get("/api/files").param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[*].name", hasItem(sheet.getFileName().toString())));
        mockMvc.perform(get("/file/" + id).param("download", "true").param("t", token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString(sheet.getFileName().toString())));
    }
}
