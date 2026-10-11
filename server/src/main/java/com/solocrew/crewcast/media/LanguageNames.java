package com.solocrew.crewcast.media;

import java.util.Locale;
import java.util.Map;

public final class LanguageNames {

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("en", "English"),
            Map.entry("hi", "Hindi"),
            Map.entry("ml", "Malayalam"),
            Map.entry("ta", "Tamil"),
            Map.entry("te", "Telugu"),
            Map.entry("kn", "Kannada"),
            Map.entry("es", "Spanish"),
            Map.entry("fr", "French"),
            Map.entry("de", "German"),
            Map.entry("ja", "Japanese"));

    private LanguageNames() {
    }

    public static String displayName(String langTag, int trackNumber) {
        String norm = AudioTrackSelector.normalizeLanguage(langTag);
        if (!norm.isBlank() && NAMES.containsKey(norm)) {
            return NAMES.get(norm);
        }
        if (langTag != null && !langTag.isBlank()) {
            return langTag.toUpperCase(Locale.ROOT);
        }
        return "Track " + (trackNumber + 1);
    }
}
