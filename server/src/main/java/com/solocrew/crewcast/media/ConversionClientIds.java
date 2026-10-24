package com.solocrew.crewcast.media;

public final class ConversionClientIds {

    private ConversionClientIds() {
    }

    /** Accept opaque tab ids from sessionStorage (alphanumeric, dash, underscore; max 64 chars). */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 64) {
            return null;
        }
        if (!trimmed.matches("[a-zA-Z0-9_-]+")) {
            return null;
        }
        return trimmed;
    }
}
