package com.solocrew.crewcast.media;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CompatTranscodePlanner {

    private CompatTranscodePlanner() {
    }

    static List<String> buildCommand(
            Path ffmpeg,
            Path input,
            ProbedMedia media,
            int audioRelativeIndex,
            Path outputPart,
            String videoEncoder,
            int maxHeight,
            boolean tonemapHdr) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg.toString());
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");
        cmd.add("-y");
        cmd.add("-threads");
        cmd.add("2");
        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());
        cmd.add("-map");
        cmd.add("0:" + media.mainVideoAbsoluteIndex());
        if (!media.audioTracks().isEmpty() && audioRelativeIndex >= 0
                && audioRelativeIndex < media.audioTracks().size()) {
            cmd.add("-map");
            cmd.add("0:a:" + audioRelativeIndex);
        }
        cmd.add("-sn");
        cmd.add("-dn");
        cmd.add("-map_metadata");
        cmd.add("-1");

        String vf = buildVideoFilter(maxHeight, tonemapHdr);
        if (!vf.isEmpty()) {
            cmd.add("-vf");
            cmd.add(vf);
        }

        cmd.add("-c:v");
        cmd.add(videoEncoder);
        cmd.add("-profile:v");
        cmd.add("high");
        cmd.add("-level");
        cmd.add("4.1");
        int bitrateMbps = maxHeight >= 1080 ? 5 : 3;
        cmd.add("-b:v");
        cmd.add(bitrateMbps + "M");
        cmd.add("-maxrate");
        cmd.add((bitrateMbps + 1) + "M");
        cmd.add("-bufsize");
        cmd.add((bitrateMbps * 2) + "M");

        if (!media.audioTracks().isEmpty() && audioRelativeIndex >= 0) {
            AudioStreamInfo audio = media.audioByRelativeIndex(audioRelativeIndex).orElse(null);
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("192k");
            if (audio != null && FfmpegTranscodePlanner.needs51Downmix(audio)) {
                cmd.add("-af");
                cmd.add(FfmpegTranscodePlanner.DOWNMIX_51_FILTER);
            } else {
                cmd.add("-ac");
                cmd.add("2");
            }
        } else {
            cmd.add("-an");
        }

        cmd.add("-movflags");
        cmd.add("+faststart");
        cmd.add("-progress");
        cmd.add("pipe:1");
        cmd.add("-nostats");
        cmd.add("-f");
        cmd.add("mp4");
        cmd.add(outputPart.toAbsolutePath().toString());
        return List.copyOf(cmd);
    }

    private static String buildVideoFilter(int maxHeight, boolean tonemapHdr) {
        int maxW = maxHeight >= 1080 ? 1920 : 1280;
        int maxH = maxHeight >= 1080 ? 1080 : 720;
        String scale = "scale=w=min(iw\\," + maxW + "):h=min(ih\\," + maxH
                + "):force_original_aspect_ratio=decrease";
        if (tonemapHdr) {
            return scale + ",zscale=transfer=linear:npl=100,format=gpf32le,"
                    + "zscale=primaries=709:transfer=709:matrix=709:range=pc,format=yuv420p";
        }
        return scale + ",format=yuv420p";
    }

    static boolean encoderLikelySupportsTonemap(String encoder) {
        if (encoder == null) {
            return false;
        }
        String e = encoder.toLowerCase(Locale.ROOT);
        return !e.contains("videotoolbox") && !e.contains("nvenc") && !e.contains("qsv") && !e.contains("amf");
    }
}
