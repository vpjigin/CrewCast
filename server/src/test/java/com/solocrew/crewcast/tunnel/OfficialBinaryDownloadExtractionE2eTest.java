package com.solocrew.crewcast.tunnel;

import com.solocrew.crewcast.media.FfmpegDownloader;
import com.solocrew.crewcast.media.FfmpegManager;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import com.solocrew.crewcast.system.CrewcastPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Downloads official ffmpeg and cloudflared archives through production code (checksum + SafeArchive).
 */
@Tag("e2e")
@SpringBootTest(properties = "server.port=0")
class OfficialBinaryDownloadExtractionE2eTest extends CrewcastSpringTestBase {

    @Autowired
    FfmpegDownloader ffmpegDownloader;
    @Autowired
    FfmpegManager ffmpegManager;
    @Autowired
    CloudflaredManager cloudflaredManager;

    @BeforeEach
    void networkAndCleanBin() throws Exception {
        assumeTrue(networkAvailable(), "No network — skipping official binary download e2e");
        Path bin = isolatedHome().resolve("bin");
        if (Files.isDirectory(bin)) {
            try (var walk = Files.walk(bin)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
        Files.createDirectories(bin);
        System.setProperty("crewcast.ffmpeg.forceDownload", "true");
        System.setProperty("crewcast.ffmpeg.skipSystemSearch", "true");
        System.setProperty("crewcast.tunnel.forceDownload", "true");
        ffmpegManager.recheck();
    }

    @Test
    void ffmpegDownloadVerifyExtractAndVersion() throws Exception {
        ffmpegDownloader.downloadIfNeeded(true);
        ffmpegManager.recheck();
        assertTrue(ffmpegManager.isAvailable());
        runVersion(FfmpegDownloader.ffmpegLocalPath());
        runVersion(FfmpegDownloader.ffprobeLocalPath());
    }

    @Test
    void cloudflaredDownloadVerifyExtractAndVersion() throws Exception {
        Path dest = CrewcastPaths.binDir().resolve(isWindows() ? "cloudflared.exe" : "cloudflared");
        Files.deleteIfExists(dest);
        cloudflaredManager.downloadLatestReleaseForTest(dest);
        assertTrue(Files.isRegularFile(dest));
        assertTrue(Files.size(dest) > 0);
        if (!isWindows()) {
            assertTrue(Files.isExecutable(dest), "cloudflared should be executable after extract");
        }
        runVersion(dest);
    }

    private static void runVersion(Path binary) throws Exception {
        Process p = new ProcessBuilder(binary.toString(), "-version")
                .redirectErrorStream(true)
                .start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        assertTrue(p.exitValue() == 0);
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(out.length() > 5, "Expected version output from " + binary.getFileName());
    }

    private static boolean networkAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("github.com", 443), 4000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
