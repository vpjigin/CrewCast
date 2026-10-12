package com.solocrew.crewcast.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Picks a working H.264 encoder from the bundled ffmpeg (hardware first, software last). */
public class H264EncoderDetector {

    private static final Logger log = LoggerFactory.getLogger(H264EncoderDetector.class);

    private final Path ffmpeg;
    private volatile String cachedEncoder;

    public H264EncoderDetector(Path ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    public synchronized String resolveEncoder() throws IOException, InterruptedException {
        if (cachedEncoder != null) {
            return cachedEncoder;
        }
        Set<String> listed = listH264EncodersFromFfmpeg();
        List<String> order = candidateOrder();
        for (String name : order) {
            if (!listed.contains(name)) {
                continue;
            }
            if (verifyEncoderWorks(name)) {
                cachedEncoder = name;
                log.info("Crewcast compat: using H.264 encoder {}", name);
                return name;
            }
        }
        return null;
    }

    /** Test hook: inject encoder list output without running ffmpeg. */
    static Set<String> parseEncodersOutput(String ffmpegEncodersOutput) {
        Set<String> out = new LinkedHashSet<>();
        for (String line : ffmpegEncodersOutput.split("\n")) {
            line = line.trim();
            if (!line.startsWith("V") || line.length() < 8) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length >= 2 && (parts[1].startsWith("h264_") || isSoftwareH264(parts[1]))) {
                out.add(parts[1]);
            }
        }
        return out;
    }

    private static boolean isSoftwareH264(String name) {
        return "libx264".equals(name) || "libopenh264".equals(name);
    }

    void clearCacheForTests() {
        cachedEncoder = null;
    }

    private Set<String> listH264EncodersFromFfmpeg() throws IOException, InterruptedException {
        Process p = new ProcessBuilder(ffmpeg.toString(), "-hide_banner", "-encoders")
                .redirectErrorStream(true)
                .start();
        String output;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            output = r.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
        }
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("ffmpeg -encoders timed out");
        }
        return parseEncodersOutput(output);
    }

    private boolean verifyEncoderWorks(String encoder) throws IOException, InterruptedException {
        List<String> cmd = List.of(
                ffmpeg.toString(),
                "-hide_banner",
                "-loglevel", "error",
                "-y",
                "-f", "lavfi",
                "-i", "color=c=black:s=64x64:d=1",
                "-c:v", encoder,
                "-f", "null",
                "-");
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            while (r.readLine() != null) {
                // drain
            }
        }
        if (!p.waitFor(45, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return false;
        }
        return p.exitValue() == 0;
    }

    static List<String> candidateOrder() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> list = new ArrayList<>();
        if (os.contains("mac")) {
            list.add("h264_videotoolbox");
        } else if (os.contains("win")) {
            list.add("h264_nvenc");
            list.add("h264_qsv");
            list.add("h264_amf");
        } else {
            list.add("h264_vaapi");
            list.add("h264_nvenc");
            list.add("h264_qsv");
        }
        list.add("libx264");
        list.add("libopenh264");
        return list;
    }
}
