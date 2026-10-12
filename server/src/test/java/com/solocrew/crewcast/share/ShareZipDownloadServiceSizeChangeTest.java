package com.solocrew.crewcast.share;

import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "server.port=0")
class ShareZipDownloadServiceSizeChangeTest extends CrewcastSpringTestBase {

    @Autowired
    ShareZipDownloadService zipDownloadService;
    @Autowired
    ShareRegistry shareRegistry;
    @Autowired
    SharingState sharingState;
    @Autowired
    SettingsStore settingsStore;

    @BeforeEach
    void sharing() throws Exception {
        settingsStore.getSettings().setShares(new java.util.ArrayList<>());
        settingsStore.save();
        sharingState.setSharingActive(true);
    }

    @Test
    void writeAbortsWhenFileShrinksAfterPlanning() throws Exception {
        Path dir = Files.createTempDirectory("crewcast-zip-mutate-");
        Path f = dir.resolve("mut.txt");
        Files.writeString(f, "1234567890", StandardCharsets.UTF_8);
        shareRegistry.addFolder(dir);
        String folderId = ShareRegistry.idForPath(dir);

        ShareZipDownloadService.PreparedDownload prepared = zipDownloadService.prepareDownload(folderId);
        assertTrue(prepared.contentLength() > 0);
        Files.writeString(f, "x", StandardCharsets.UTF_8);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> zipDownloadService.writeZip(prepared, out, () -> true));
    }
}
