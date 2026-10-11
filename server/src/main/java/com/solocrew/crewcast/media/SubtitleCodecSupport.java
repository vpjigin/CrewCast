package com.solocrew.crewcast.media;

import java.util.Locale;
import java.util.Set;

final class SubtitleCodecSupport {

    private static final Set<String> TEXT = Set.of(
            "subrip", "srt", "ass", "ssa", "mov_text", "webvtt", "text");

    private static final Set<String> BITMAP = Set.of(
            "hdmv_pgs_subtitle", "pgssub", "dvd_subtitle", "dvdsub", "dvb_subtitle", "dvb");

    private SubtitleCodecSupport() {
    }

    static boolean isTextCodec(String codecName) {
        if (codecName == null || codecName.isBlank()) {
            return false;
        }
        return TEXT.contains(codecName.toLowerCase(Locale.ROOT));
    }

    static boolean isBitmapCodec(String codecName) {
        if (codecName == null || codecName.isBlank()) {
            return false;
        }
        return BITMAP.contains(codecName.toLowerCase(Locale.ROOT));
    }
}
