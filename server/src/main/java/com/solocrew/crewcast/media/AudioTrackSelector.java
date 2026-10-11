package com.solocrew.crewcast.media;

import java.util.Locale;
import java.util.OptionalInt;

public final class AudioTrackSelector {

    private AudioTrackSelector() {
    }

    /**
     * @param audioQuery optional {@code ?audio=N} (audio-relative index)
     * @param preferredLanguage app setting (e.g. {@code hi}, {@code ml})
     */
    public static int selectAudioIndex(ProbedMedia media, Integer audioQuery, String preferredLanguage) {
        if (media.audioTracks().isEmpty()) {
            return 0;
        }
        if (audioQuery != null) {
            int idx = audioQuery;
            if (idx >= 0 && idx < media.audioTracks().size()) {
                return idx;
            }
        }
        String want = normalizeLanguage(preferredLanguage);
        if (!want.isEmpty()) {
            for (AudioStreamInfo track : media.audioTracks()) {
                if (languageMatches(want, track.languageTag())) {
                    return track.audioIndex();
                }
            }
        }
        return media.audioTracks().getFirst().audioIndex();
    }

    public static OptionalInt parseAudioQuery(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    static boolean languageMatches(String preferred, String streamTag) {
        if (streamTag == null || streamTag.isBlank()) {
            return false;
        }
        return normalizeLanguage(streamTag).equals(preferred);
    }

    static String normalizeLanguage(String tag) {
        if (tag == null || tag.isBlank()) {
            return "";
        }
        String t = tag.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("hin") || t.equals("hi")) {
            return "hi";
        }
        if (t.startsWith("mal") || t.equals("ml")) {
            return "ml";
        }
        if (t.length() >= 3 && t.length() <= 3) {
            return t.substring(0, 2);
        }
        return t.length() >= 2 ? t.substring(0, 2) : t;
    }
}
