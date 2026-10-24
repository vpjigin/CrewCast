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
                "-show_format",
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
        VideoFormat videoFormat = VideoFormat.unknown();
        List<AudioStreamInfo> audio = new ArrayList<>();
        int audioCounter = 0;
        List<SubtitleStreamInfo> subtitles = new ArrayList<>();
        int subtitleCounter = 0;

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
                    videoFormat = parseVideoFormat(stream);
                }
                continue;
            }
            if (isAudioStream(type, stream)) {
                JsonNode tags = stream.path("tags");
                audio.add(new AudioStreamInfo(
                        index,
                        audioCounter++,
                        text(stream, "codec_name"),
                        text(tags, "language"),
                        text(tags, "title"),
                        stream.path("channels").asInt(0),
                        text(stream, "channel_layout")));
                continue;
            }
            if ("subtitle".equals(type)) {
                JsonNode tags = stream.path("tags");
                JsonNode disposition = stream.path("disposition");
                String codec = text(stream, "codec_name");
                boolean supported = SubtitleCodecSupport.isTextCodec(codec);
                subtitles.add(new SubtitleStreamInfo(
                        index,
                        subtitleCounter++,
                        codec,
                        text(tags, "language"),
                        text(tags, "title"),
                        disposition.path("forced").asInt(0) == 1,
                        disposition.path("default").asInt(0) == 1,
                        supported));
            }
        }

        if (mainVideoIndex == null) {
            throw new IOException("No main video stream found (attached_pic streams ignored)");
        }

        boolean hevcWarning = isHevcCodec(mainVideoCodec);
        long formatBitRate = 0;
        double formatDurationSec = 0;
        JsonNode format = root.get("format");
        if (format != null) {
            formatBitRate = format.path("bit_rate").asLong(0);
            String dur = format.path("duration").asText("");
            if (!dur.isBlank()) {
                try {
                    formatDurationSec = Double.parseDouble(dur.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return new ProbedMedia(
                mainVideoIndex,
                mainVideoCodec,
                hevcWarning,
                List.copyOf(audio),
                List.copyOf(subtitles),
                videoFormat,
                formatBitRate,
                formatDurationSec);
    }

    static VideoFormat parseVideoFormat(JsonNode stream) {
        int w = stream.path("width").asInt(0);
        int h = stream.path("height").asInt(0);
        double fps = parseFrameRate(text(stream, "avg_frame_rate"));
        if (fps <= 0) {
            fps = parseFrameRate(text(stream, "r_frame_rate"));
        }
        String pixFmt = text(stream, "pix_fmt");
        String profile = text(stream, "profile");
        int bitDepth = inferBitDepth(pixFmt, stream.path("bits_per_raw_sample").asInt(0));
        JsonNode tags = stream.path("tags");
        String transfer = text(tags, "color_transfer");
        if (transfer.isEmpty()) {
            transfer = text(stream, "color_transfer");
        }
        String primaries = text(tags, "color_primaries");
        if (primaries.isEmpty()) {
            primaries = text(stream, "color_primaries");
        }
        return new VideoFormat(w, h, fps, pixFmt, profile, bitDepth, transfer, primaries);
    }

    private static double parseFrameRate(String raw) {
        if (raw == null || raw.isBlank() || "0/0".equals(raw)) {
            return 0;
        }
        if (raw.contains("/")) {
            String[] parts = raw.split("/");
            if (parts.length == 2) {
                try {
                    double num = Double.parseDouble(parts[0]);
                    double den = Double.parseDouble(parts[1]);
                    return den > 0 ? num / den : 0;
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static int inferBitDepth(String pixFmt, int bitsPerRawSample) {
        if (bitsPerRawSample > 0) {
            return bitsPerRawSample;
        }
        if (pixFmt == null) {
            return 8;
        }
        String p = pixFmt.toLowerCase(Locale.ROOT);
        if (p.contains("12le") || p.contains("12be")) {
            return 12;
        }
        if (p.contains("10le") || p.contains("10be") || p.contains("p010") || p.contains("p016")) {
            return 10;
        }
        return 8;
    }

    static boolean isAudioStream(String codecType, JsonNode stream) {
        if ("audio".equals(codecType)) {
            return true;
        }
        if (codecType != null && !codecType.isBlank()) {
            return false;
        }
        String codec = text(stream, "codec_name").toLowerCase(Locale.ROOT);
        if (codec.isEmpty()) {
            return false;
        }
        return codec.startsWith("aac")
                || codec.equals("mp3")
                || codec.equals("opus")
                || codec.equals("flac")
                || codec.equals("vorbis")
                || codec.equals("ac3")
                || codec.equals("eac3")
                || codec.equals("dts")
                || codec.equals("truehd")
                || codec.startsWith("pcm");
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
