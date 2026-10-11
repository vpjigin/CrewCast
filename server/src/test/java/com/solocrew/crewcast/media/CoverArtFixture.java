package com.solocrew.crewcast.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Builds a small MP4 with HEVC + dual E-AC3 (hin/mal) + mjpeg attached_pic cover via ffmpeg lavfi. */
final class CoverArtFixture {

    private CoverArtFixture() {
    }

    static Path create() throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("crewcast-cover-art-fixture");
        Path cover = dir.resolve("cover.png");
        Files.write(cover, java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="));
        Path out = dir.resolve("fixture.mp4");
        List<String> cmd = List.of(
                "ffmpeg", "-y", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc=size=320x240:rate=1:duration=1",
                "-f", "lavfi", "-i", "sine=f=440:sample_rate=48000:duration=1",
                "-f", "lavfi", "-i", "sine=f=880:sample_rate=48000:duration=1",
                "-loop", "1", "-framerate", "1", "-t", "1", "-i", cover.toString(),
                "-map", "0:v:0", "-map", "1:a:0", "-map", "2:a:0", "-map", "3:v:0",
                "-c:v:0", "libx265", "-pix_fmt:v:0", "yuv420p", "-tag:v:0", "hvc1",
                "-c:a", "eac3", "-ac", "6", "-channel_layout", "5.1",
                "-c:v:1", "mjpeg",
                "-metadata:s:a:0", "language=hin", "-metadata:s:a:0", "title=Org JHS Hindi DDP 5.1",
                "-metadata:s:a:1", "language=mal", "-metadata:s:a:1", "title=Org Malayalam DDP 5.1",
                "-disposition:v:1", "attached_pic",
                out.toString());
        run(cmd);
        return out;
    }

    private static void run(List<String> cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("ffmpeg timed out");
        }
        if (p.exitValue() != 0) {
            throw new IOException("ffmpeg failed: exit " + p.exitValue());
        }
    }
}
