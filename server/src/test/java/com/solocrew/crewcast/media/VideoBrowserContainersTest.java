package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.ShareEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoBrowserContainersTest {

    @TempDir
    Path temp;

    @Test
    void nativeContainers() {
        assertTrue(VideoBrowserContainers.isBrowserNativeContainer("a.mp4"));
        assertTrue(VideoBrowserContainers.isBrowserNativeContainer("a.M4V"));
        assertTrue(VideoBrowserContainers.isBrowserNativeContainer("clip.mov"));
        assertFalse(VideoBrowserContainers.isBrowserNativeContainer("film.mkv"));
        assertFalse(VideoBrowserContainers.isBrowserNativeContainer("clip.webm"));
    }

    @Test
    void fileShareDetectsMkv() throws Exception {
        Path mkv = temp.resolve("x.mkv");
        Files.write(mkv, new byte[]{0});
        assertTrue(VideoBrowserContainers.addedShareIncludesNonNativeVideo(mkv, ShareEntry.Kind.FILE));
    }

    @Test
    void fileShareIgnoresMp4() throws Exception {
        Path mp4 = temp.resolve("x.mp4");
        Files.write(mp4, new byte[]{0});
        assertFalse(VideoBrowserContainers.addedShareIncludesNonNativeVideo(mp4, ShareEntry.Kind.FILE));
    }

    @Test
    void folderWithOnlyMp4() throws Exception {
        Files.write(temp.resolve("a.mp4"), new byte[]{0});
        assertFalse(VideoBrowserContainers.addedShareIncludesNonNativeVideo(temp, ShareEntry.Kind.FOLDER));
    }

    @Test
    void folderWithMkvNested() throws Exception {
        Path sub = temp.resolve("sub");
        Files.createDirectories(sub);
        Files.write(sub.resolve("b.mkv"), new byte[]{0});
        assertTrue(VideoBrowserContainers.addedShareIncludesNonNativeVideo(temp, ShareEntry.Kind.FOLDER));
    }
}
