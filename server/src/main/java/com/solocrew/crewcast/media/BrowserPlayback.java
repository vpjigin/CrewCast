package com.solocrew.crewcast.media;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class BrowserPlayback {

    private static final Set<String> BROWSER_AUDIO = Set.of("aac", "mp3", "opus", "flac", "mp4a");

    private BrowserPlayback() {
    }

    public static boolean isBrowserContainer(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp4") || name.endsWith(".m4v") || name.endsWith(".mov");
    }

    public static boolean isBrowserAudioCodec(String codec) {
        if (codec == null || codec.isBlank()) {
            return false;
        }
        String c = codec.toLowerCase(Locale.ROOT);
        if (BROWSER_AUDIO.contains(c)) {
            return true;
        }
        return c.startsWith("aac");
    }

    public static boolean canServeOriginal(Path file, ProbedMedia media, int audioRelativeIndex) {
        if (!isBrowserContainer(file)) {
            return false;
        }
        return media.audioByRelativeIndex(audioRelativeIndex)
                .map(a -> isBrowserAudioCodec(a.codecName()))
                .orElse(false);
    }
}
