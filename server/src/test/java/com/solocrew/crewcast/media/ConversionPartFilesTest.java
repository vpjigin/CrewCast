package com.solocrew.crewcast.media;

import com.solocrew.crewcast.system.CrewcastPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionPartFilesTest {

    @TempDir
    Path cache;

    @Test
    void deletesStaleCompatPartsOnly() throws Exception {
        Files.writeString(cache.resolve("compat-abc.mp4.part"), "x");
        Files.writeString(cache.resolve("deadbeef.mp4.part"), "y");
        ConversionPartFiles.deleteStaleCompatParts(cache);
        assertFalse(Files.exists(cache.resolve("compat-abc.mp4.part")));
        assertTrue(Files.exists(cache.resolve("deadbeef.mp4.part")));
    }
}
