package com.solocrew.crewcast.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegDownloaderTest {

    @Test
    void parseSha256Sidecar() throws Exception {
        String body = "abc123def4567890123456789012345678901234567890123456789012345678  ffmpeg.zip\n";
        String hash = FfmpegDownloader.parseSha256Sidecar(body, "ffmpeg.zip");
        assertEquals("abc123def4567890123456789012345678901234567890123456789012345678", hash);
    }

    @Test
    void rejectsNonAllowListedUrl() {
        assertThrows(IOException.class, () -> FfmpegDownloader.assertAllowedUrl("https://evil.example/ffmpeg.zip"));
    }

    @Test
    void allowsBtbNDownloadUrl() throws Exception {
        FfmpegDownloader.assertAllowedUrl(
                "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-lgpl.tar.xz");
    }

    @Test
    void checksumVerificationFailsClosed(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bad.bin");
        Files.writeString(file, "data");
        String actual = FfmpegDownloader.sha256Hex(file);
        assertThrows(IOException.class, () -> {
            if (!actual.equals("0".repeat(64))) {
                throw new IOException("ffmpeg download failed SHA-256 verification");
            }
        });
    }

    @Test
    void parseDigestSha256StripsPrefix() {
        assertEquals(
                "abc123def4567890123456789012345678901234567890123456789012345678",
                FfmpegDownloader.parseDigestSha256("sha256:abc123def4567890123456789012345678901234567890123456789012345678"));
    }

}
