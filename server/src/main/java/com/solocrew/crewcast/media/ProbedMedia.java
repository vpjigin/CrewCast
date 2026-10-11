package com.solocrew.crewcast.media;

import java.util.List;
import java.util.Optional;

public record ProbedMedia(
        int mainVideoAbsoluteIndex,
        String mainVideoCodec,
        boolean hevcInBrowserWarning,
        List<AudioStreamInfo> audioTracks) {

    public Optional<AudioStreamInfo> audioByRelativeIndex(int audioIndex) {
        return audioTracks.stream().filter(a -> a.audioIndex() == audioIndex).findFirst();
    }
}
