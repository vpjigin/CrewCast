package com.solocrew.crewcast.media;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds ffmpeg argv for browser-friendly remux/transcode (video copy, AAC audio).
 */
public final class FfmpegTranscodePlanner {

    public static final String DOWNMIX_51_FILTER =
            "pan=stereo|FL=FC+0.707*FL+0.5*BL|FR=FC+0.707*FR+0.5*BR,alimiter=limit=0.95";

    private FfmpegTranscodePlanner() {
    }

    public static List<String> buildTranscodeCommand(
            Path ffmpeg,
            Path input,
            ProbedMedia media,
            int audioRelativeIndex,
            Path outputPartFile) {
        AudioStreamInfo audio = media.audioByRelativeIndex(audioRelativeIndex)
                .orElseThrow(() -> new IllegalArgumentException("Invalid audio index: " + audioRelativeIndex));

        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg.toString());
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());
        cmd.add("-map");
        cmd.add("0:" + media.mainVideoAbsoluteIndex());
        cmd.add("-map");
        cmd.add("0:a:" + audioRelativeIndex);
        cmd.add("-sn");
        cmd.add("-dn");
        cmd.add("-map_metadata");
        cmd.add("-1");
        cmd.add("-c:v");
        cmd.add("copy");
        if (media.hevcInBrowserWarning()) {
            cmd.add("-tag:v");
            cmd.add("hvc1");
        }
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("192k");
        if (needs51Downmix(audio)) {
            cmd.add("-af");
            cmd.add(DOWNMIX_51_FILTER);
        } else {
            cmd.add("-ac");
            cmd.add("2");
        }
        cmd.add("-movflags");
        cmd.add("+faststart");
        cmd.add("-progress");
        cmd.add("pipe:1");
        cmd.add("-nostats");
        cmd.add("-f");
        cmd.add("mp4");
        cmd.add(outputPartFile.toAbsolutePath().toString());
        return List.copyOf(cmd);
    }

    static boolean needs51Downmix(AudioStreamInfo audio) {
        if (audio.channels() >= 6) {
            return true;
        }
        String layout = audio.channelLayout() == null ? "" : audio.channelLayout().toLowerCase(Locale.ROOT);
        return layout.contains("5.1") || layout.contains("5.1(");
    }
}
