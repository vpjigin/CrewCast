package com.solocrew.crewcast.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatCacheKeysTest {

    @TempDir
    Path dir;

    @Test
    void cacheKeyStableForSameInputs() throws Exception {
        Path f = dir.resolve("movie.mkv");
        Files.writeString(f, "x");
        String a = CompatCacheKeys.cacheFileName(f, 1, 1080);
        String b = CompatCacheKeys.cacheFileName(f, 1, 1080);
        assertEquals(a, b);
        assertTrue(a.startsWith("compat-"));
        assertTrue(a.endsWith(".mp4"));
    }

    @Test
    void cacheKeyDiffersForAudioIndexAndMaxHeight() throws Exception {
        Path f = dir.resolve("movie.mkv");
        Files.writeString(f, "x");
        String base = CompatCacheKeys.cacheFileName(f, 0, 1080);
        assertNotEquals(base, CompatCacheKeys.cacheFileName(f, 1, 1080));
        assertNotEquals(base, CompatCacheKeys.cacheFileName(f, 0, 720));
    }

}
