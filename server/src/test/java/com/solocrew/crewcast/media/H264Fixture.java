package com.solocrew.crewcast.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Small H.264 + AAC MP4 (browser-native, no audio fix required). */
final class H264Fixture {

    private H264Fixture() {
    }

    static Path create() throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("crewcast-h264-fixture");
        Path out = dir.resolve("fixture-h264.mp4");
        List<String> cmd = List.of(
                "ffmpeg", "-y", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc=size=320x240:rate=10:duration=2",
                "-f", "lavfi", "-i", "sine=f=440:sample_rate=48000:duration=2",
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "128k",
                out.toString());
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("ffmpeg timed out");
        }
        if (p.exitValue() != 0) {
            throw new IOException("ffmpeg failed: exit " + p.exitValue());
        }
        return out;
    }

    /** H.264 MP4 with no audio stream (silent screen recording shape). */
    static Path createSilent() throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("crewcast-h264-silent-fixture");
        Path out = dir.resolve("fixture-silent-h264.mp4");
        List<String> cmd = List.of(
                "ffmpeg", "-y", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc=size=320x240:rate=10:duration=2",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-an",
                out.toString());
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("ffmpeg timed out");
        }
        if (p.exitValue() != 0) {
            throw new IOException("ffmpeg failed: exit " + p.exitValue());
        }
        return out;
    }
}
