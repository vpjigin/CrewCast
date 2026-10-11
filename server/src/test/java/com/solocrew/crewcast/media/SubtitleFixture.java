package com.solocrew.crewcast.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** ffmpeg-generated videos and sidecars for subtitle tests. */
final class SubtitleFixture {

    private SubtitleFixture() {
    }

    record Bundle(Path dir, Path embeddedDualMkv, Path sidecarVideo, Path sidecarEn, Path sidecarMlUtf16) {
    }

    static Bundle create() throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("crewcast-sub-fixture");
        Path enSrt = dir.resolve("sub-en.srt");
        Path hiSrt = dir.resolve("sub-hi.srt");
        Files.writeString(enSrt, minimalSrt("Hello", "1:00:00,000", "1:00:02,000"), StandardCharsets.UTF_8);
        Files.writeString(hiSrt, minimalSrt("Namaste", "1:00:02,500", "1:00:04,500"), StandardCharsets.UTF_8);

        Path dual = dir.resolve("dual-sub.mkv");
        run(List.of(
                "ffmpeg", "-y", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc=size=320x240:rate=10:duration=3",
                "-f", "lavfi", "-i", "sine=f=440:duration=3",
                "-i", enSrt.toString(),
                "-i", hiSrt.toString(),
                "-map", "0:v", "-map", "1:a", "-map", "2:s", "-map", "3:s",
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "96k",
                "-metadata:s:s:0", "language=eng",
                "-metadata:s:s:1", "language=hin",
                "-c:s", "srt",
                dual.toString()));

        Path film = dir.resolve("film.mkv");
        run(List.of(
                "ffmpeg", "-y", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc=size=320x240:rate=10:duration=3",
                "-f", "lavfi", "-i", "sine=f=880:duration=3",
                "-map", "0:v", "-map", "1:a",
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "96k",
                film.toString()));

        Path filmEn = dir.resolve("film.en.srt");
        Files.writeString(filmEn, minimalSrt("Sidecar English", "0:00:00,000", "0:00:02,000"), StandardCharsets.UTF_8);

        Path filmMl = dir.resolve("film.ml.srt");
        String malayalam = "ഹലോ"; // Malayalam hello
        byte[] mlBody = minimalSrt(malayalam, "0:00:02,500", "0:00:04,500").getBytes(StandardCharsets.UTF_16LE);
        byte[] mlBytes = new byte[2 + mlBody.length];
        mlBytes[0] = (byte) 0xFF;
        mlBytes[1] = (byte) 0xFE;
        System.arraycopy(mlBody, 0, mlBytes, 2, mlBody.length);
        Files.write(filmMl, mlBytes);

        return new Bundle(dir, dual, film, filmEn, filmMl);
    }

    private static String minimalSrt(String text, String start, String end) {
        return "1\n" + start + " --> " + end + "\n" + text + "\n\n";
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
