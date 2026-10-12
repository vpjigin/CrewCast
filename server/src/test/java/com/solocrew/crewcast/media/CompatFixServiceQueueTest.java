package com.solocrew.crewcast.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Cache keys isolate queued jobs per source, audio track, and max height (one worker drains FIFO). */
class CompatFixServiceQueueTest {

    @TempDir
    Path dir;

    @Test
    void distinctJobsForDifferentTracksDoNotShareCacheFile() throws Exception {
        Path f = dir.resolve("a.mkv");
        Files.writeString(f, "content");
        String k0 = CompatCacheKeys.cacheFileName(f, 0, 1080);
        String k1 = CompatCacheKeys.cacheFileName(f, 1, 1080);
        assertNotEquals(k0, k1);
    }
}
