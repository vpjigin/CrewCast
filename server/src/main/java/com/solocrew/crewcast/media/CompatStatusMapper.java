package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.PrepareCompatMode;

public final class CompatStatusMapper {

    private CompatStatusMapper() {
    }

    public static CompatUiState map(
            PrepareCompatMode mode,
            ProbedMedia media,
            boolean encoderAvailable,
            AudioFixStatus jobStatus,
            boolean cacheReady) {
        if (!encoderAvailable) {
            return CompatUiState.UNAVAILABLE;
        }
        if (cacheReady || jobStatus.status() == AudioFixJobState.READY) {
            return CompatUiState.READY;
        }
        if (jobStatus.status() == AudioFixJobState.FAILED) {
            return CompatUiState.FAILED;
        }
        if (jobStatus.status() == AudioFixJobState.QUEUED) {
            return CompatUiState.QUEUED;
        }
        if (jobStatus.status() == AudioFixJobState.RUNNING) {
            return CompatUiState.CONVERTING;
        }
        if (CompatPreparePolicy.videoMarkedNotNeeded(mode, media)) {
            return CompatUiState.NOT_NEEDED;
        }
        return CompatUiState.NEEDED;
    }
}
