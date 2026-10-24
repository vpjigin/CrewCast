package com.solocrew.crewcast.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Desktop share-row details: size · video · audio (no prep state). */
public final class ShareVideoDetailsMeta {

    private ShareVideoDetailsMeta() {
    }

    public static String format(Path video, ProbedMedia media, AudioFixService audioFixService)
            throws IOException, InterruptedException {
        long size = Files.size(video);
        String videoLabel = formatVideo(media);
        int audioIdx = audioFixService.resolveAudioIndex(media, null);
        String audioLabel = formatAudio(media, audioIdx);
        if (audioLabel.isEmpty()) {
            return humanSize(size) + " · " + videoLabel;
        }
        return humanSize(size) + " · " + videoLabel + " · " + audioLabel;
    }

    static String formatVideo(ProbedMedia media) {
        String codec = media.mainVideoCodec() == null ? "" : media.mainVideoCodec().toLowerCase(Locale.ROOT);
        VideoFormat v = media.video() != null ? media.video() : VideoFormat.unknown();
        int depth = v.bitDepth() > 0 ? v.bitDepth() : 8;
        String label;
        if (MediaProbe.isHevcCodec(codec)) {
            label = "HEVC";
        } else if (codec.equals("h264") || codec.equals("avc1")) {
            label = "H.264";
        } else if (codec.isBlank()) {
            label = "Video";
        } else {
            label = codec.toUpperCase(Locale.ROOT);
        }
        if (depth > 8) {
            label = label + " " + depth + "-bit";
        }
        return label;
    }

    static String formatAudio(ProbedMedia media, int audioIndex) {
        return media.audioByRelativeIndex(audioIndex)
                .map(t -> displayCodec(t.codecName()))
                .orElse("");
    }

    private static String displayCodec(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String c = raw.toLowerCase(Locale.ROOT);
        if (c.equals("ac3") || c.equals("eac3")) {
            return "AC3";
        }
        if (c.equals("aac")) {
            return "AAC";
        }
        if (c.equals("dts")) {
            return "DTS";
        }
        if (c.equals("opus")) {
            return "Opus";
        }
        if (c.equals("mp3")) {
            return "MP3";
        }
        return raw.toUpperCase(Locale.ROOT);
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int u = -1;
        while (value >= 1024 && u < units.length - 1) {
            value /= 1024;
            u++;
        }
        return String.format(Locale.US, "%.0f %s", value, units[u]);
    }
}
