package com.solocrew.crewcast.media;

/** Embedded subtitle stream from ffprobe (index is among subtitle streams, not absolute). */
public record SubtitleStreamInfo(
        int absoluteStreamIndex,
        int subtitleIndex,
        String codecName,
        String languageTag,
        String title,
        boolean forced,
        boolean isDefault,
        boolean supported) {
}
