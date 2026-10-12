package com.solocrew.crewcast.web;

import com.solocrew.crewcast.access.AccessSession;
import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
class DownloadAllApiTest extends CrewcastSpringTestBase {

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

        shareDir = Files.createTempDirectory("crewcast-dlall-");
        shareDir.toFile().deleteOnExit();
        Path inner = Files.createDirectories(shareDir.resolve("inner"));
        Files.writeString(inner.resolve("deep.txt"), "deep-content", StandardCharsets.UTF_8);
        Files.writeString(shareDir.resolve("notes.pdf"), "%PDF", StandardCharsets.UTF_8);
        Files.writeString(shareDir.resolve(".hidden"), "nope", StandardCharsets.UTF_8);
        shareRegistry.addFolder(shareDir);
    }

    @Test
    void nestedFolderZipContainsRelativePathsAndContent() throws Exception {
        String innerId = ShareRegistry.idForPath(shareDir.resolve("inner"));
        MvcResult result = mockMvc.perform(get("/api/download-all").param("folder", innerId).param("t", token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inner.zip")))
                .andReturn();

        Map<String, String> entries = readZipTextEntries(result.getResponse().getContentAsByteArray());
        assertEquals("deep-content", entries.get("deep.txt"));
        assertNull(entries.get(".hidden"));
        assertNull(entries.get("notes.pdf"));
    }

    @Test
    void topLevelZipUsesDefaultNameAndIncludesShareFolderPrefix() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/download-all").param("t", token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("Crewcast-shared.zip")))
                .andReturn();

        Map<String, String> entries = readZipTextEntries(result.getResponse().getContentAsByteArray());
        String folderName = shareDir.getFileName().toString();
        assertEquals("%PDF", entries.get(folderName + "/notes.pdf"));
        assertEquals("deep-content", entries.get(folderName + "/inner/deep.txt"));
        assertNull(entries.get(folderName + "/.hidden"));
    }

    @Test
    void unknownFolderReturns404() throws Exception {
        mockMvc.perform(get("/api/download-all").param("folder", "deadbeef").param("t", token))
                .andExpect(status().isNotFound());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void symlinkOutsideShareExcludedFromZip() throws Exception {
        Path outside = Files.createTempFile("outside-dlall-", ".txt");
        outside.toFile().deleteOnExit();
        Files.writeString(outside, "secret", StandardCharsets.UTF_8);
        Path link = shareDir.resolve("escape.txt");
        Files.createSymbolicLink(link, outside);

        String innerId = ShareRegistry.idForPath(shareDir);
        MvcResult result = mockMvc.perform(get("/api/download-all").param("folder", innerId).param("t", token))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, String> entries = readZipTextEntries(result.getResponse().getContentAsByteArray());
        assertFalse(entries.containsKey("escape.txt"));
    }

    private static Map<String, String> readZipTextEntries(byte[] zipBytes) throws Exception {
        Map<String, String> out = new HashMap<>();
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipArchiveEntry entry;
            while ((entry = zis.getNextZipEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] buf = zis.readAllBytes();
                out.put(entry.getName(), new String(buf, StandardCharsets.UTF_8));
            }
        }
        return out;
    }
}
