package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.PrepareCompatMode;

/** When to auto-prepare or treat a file as needing a TV-safe transcode. */
public final class CompatPreparePolicy {

    private CompatPreparePolicy() {
    }

    public static boolean shouldAutoPrepare(PrepareCompatMode mode, ProbedMedia media) {
        if (mode == null || mode == PrepareCompatMode.OFF) {
            return false;
        }
        if (mode == PrepareCompatMode.ALL_VIDEOS) {
            return true;
        }
        return CompatPlaybackPolicy.videoNeedsCompatTranscode(media);
    }

    public static boolean videoMarkedNotNeeded(PrepareCompatMode mode, ProbedMedia media) {
        if (mode == PrepareCompatMode.ALL_VIDEOS) {
            return false;
        }
        return !CompatPlaybackPolicy.videoNeedsCompatTranscode(media);
    }
}
