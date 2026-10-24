package com.solocrew.crewcast.media;

import java.util.List;
import java.util.Optional;

public record ProbedMedia(
        int mainVideoAbsoluteIndex,
        String mainVideoCodec,
        boolean hevcInBrowserWarning,
        List<AudioStreamInfo> audioTracks,
        List<SubtitleStreamInfo> subtitleTracks,
        VideoFormat video,
        long formatBitRateBps,
        double formatDurationSec) {

    public ProbedMedia(
            int mainVideoAbsoluteIndex,
            String mainVideoCodec,
            boolean hevcInBrowserWarning,
            List<AudioStreamInfo> audioTracks) {
        this(mainVideoAbsoluteIndex, mainVideoCodec, hevcInBrowserWarning, audioTracks, List.of(), VideoFormat.unknown(), 0, 0);
    }

    public ProbedMedia(
            int mainVideoAbsoluteIndex,
            String mainVideoCodec,
            boolean hevcInBrowserWarning,
            List<AudioStreamInfo> audioTracks,
            List<SubtitleStreamInfo> subtitleTracks,
            VideoFormat video) {
        this(mainVideoAbsoluteIndex, mainVideoCodec, hevcInBrowserWarning, audioTracks, subtitleTracks, video, 0, 0);
    }

    public ProbedMedia(
            int mainVideoAbsoluteIndex,
            String mainVideoCodec,
            boolean hevcInBrowserWarning,
            List<AudioStreamInfo> audioTracks,
            List<SubtitleStreamInfo> subtitleTracks,
            VideoFormat video,
            long formatBitRateBps) {
        this(mainVideoAbsoluteIndex, mainVideoCodec, hevcInBrowserWarning, audioTracks, subtitleTracks, video, formatBitRateBps, 0);
    }

    public Optional<AudioStreamInfo> audioByRelativeIndex(int audioIndex) {
        return audioTracks.stream().filter(a -> a.audioIndex() == audioIndex).findFirst();
    }
}
