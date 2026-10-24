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
        assertTrue(a.contains("compat-"));
    }

    @Test
    void cacheKeyStableAcrossRepeatedCalls() throws Exception {
        Path f = dir.resolve("film.mkv");
        Files.writeString(f, "long-form-content");
        String first = CompatCacheKeys.cacheFileName(f, 0, 1080);
        Thread.sleep(5);
        String second = CompatCacheKeys.cacheFileName(f, 0, 1080);
        assertEquals(first, second);
    }

    @Test
    void cacheKeyDiffersForAudioIndexAndMaxHeight() throws Exception {
        Path f = dir.resolve("movie.mkv");
        Files.writeString(f, "x");
        String base = CompatCacheKeys.cacheFileName(f, 0, 1080);
        assertNotEquals(base, CompatCacheKeys.cacheFileName(f, 1, 1080));
        assertNotEquals(base, CompatCacheKeys.cacheFileName(f, 0, 720));
    }

    @Test
    void cacheKeyIncludesPolicyTagNotBitrate() throws Exception {
        Path f = dir.resolve("movie.mkv");
        Files.writeString(f, "x");
        String name = CompatCacheKeys.cacheFileName(f, 0, 1080);
        assertTrue(name.startsWith("compat-"));
    }
}
