package com.solocrew.crewcast.media;

/**
 * One audio stream in the container. {@link #audioIndex()} is audio-relative (0 = first audio track).
 */
public record AudioStreamInfo(
        int absoluteIndex,
        int audioIndex,
        String codecName,
        String languageTag,
        String title,
        int channels,
        String channelLayout) {
}
