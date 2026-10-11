package com.solocrew.crewcast.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class MediaProbe {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MediaProbe() {
    }

    public static ProbedMedia probe(Path file) throws IOException, InterruptedException {
        return probe(file, Path.of("ffprobe"));
    }

    public static ProbedMedia probe(Path file, Path ffprobeBinary) throws IOException, InterruptedException {
        List<String> cmd = List.of(
                ffprobeBinary.toString(),
                "-v", "error",
                "-show_streams",
                "-print_format", "json",
                file.toAbsolutePath().toString());
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String json;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(line);
            }
            json = sb.toString();
        }
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("ffprobe timed out");
        }
        if (process.exitValue() != 0) {
            throw new IOException("ffprobe failed (exit " + process.exitValue() + "): " + json);
        }
        return parse(json);
    }

    static ProbedMedia parse(String ffprobeJson) throws IOException {
        JsonNode root = JSON.readTree(ffprobeJson);
        JsonNode streams = root.get("streams");
        if (streams == null || !streams.isArray()) {
            throw new IOException("ffprobe JSON missing streams");
        }

        Integer mainVideoIndex = null;
        String mainVideoCodec = null;
        List<AudioStreamInfo> audio = new ArrayList<>();
        int audioCounter = 0;

        for (JsonNode stream : streams) {
            String type = text(stream, "codec_type");
            int index = stream.path("index").asInt(-1);
            if ("video".equals(type)) {
                if (isAttachedPicture(stream)) {
                    continue;
                }
                if (mainVideoIndex == null) {
                    mainVideoIndex = index;
                    mainVideoCodec = text(stream, "codec_name");
                }
                continue;
            }
            if ("audio".equals(type)) {
                JsonNode tags = stream.path("tags");
                audio.add(new AudioStreamInfo(
                        index,
                        audioCounter++,
                        text(stream, "codec_name"),
                        text(tags, "language"),
                        text(tags, "title"),
                        stream.path("channels").asInt(0),
                        text(stream, "channel_layout")));
            }
        }

        if (mainVideoIndex == null) {
            throw new IOException("No main video stream found (attached_pic streams ignored)");
        }

        boolean hevcWarning = isHevcCodec(mainVideoCodec);
        return new ProbedMedia(mainVideoIndex, mainVideoCodec, hevcWarning, List.copyOf(audio));
    }

    static boolean isAttachedPicture(JsonNode stream) {
        JsonNode disposition = stream.path("disposition");
        if (disposition.path("attached_pic").asInt(0) == 1) {
            return true;
        }
        // Matroska cover art is sometimes mjpeg without disposition; treat obvious poster streams as non-main.
        String codec = text(stream, "codec_name");
        if ("mjpeg".equalsIgnoreCase(codec)) {
            int w = stream.path("width").asInt(0);
            int h = stream.path("height").asInt(0);
            if (w > 0 && h > 0 && w <= 32 && h <= 32) {
                return true;
            }
        }
        return false;
    }

    static boolean isHevcCodec(String codec) {
        if (codec == null) {
            return false;
        }
        String c = codec.toLowerCase(Locale.ROOT);
        return c.equals("hevc") || c.equals("h265");
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }
}
