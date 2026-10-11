package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.FfmpegDownloadConsent;

/** When the desktop app may show the ffmpeg consent dialog (headless excluded). */
public final class FfmpegPromptPolicy {

    public enum Trigger {
        START_SHARING,
        ADDED_NON_NATIVE_VIDEO,
        USER_ENABLED_FIX_AUDIO_TOGGLE
    }

    private FfmpegPromptPolicy() {
    }

    public static boolean shouldShowFixAudioMutedHint(
            boolean ffmpegAvailable, boolean fixAudioForBrowsers, FfmpegDownloadConsent consent) {
        if (ffmpegAvailable || fixAudioForBrowsers) {
            return false;
        }
        return consent == FfmpegDownloadConsent.DECLINED || consent == FfmpegDownloadConsent.ALLOWED;
    }

    public static boolean shouldShowPrompt(
            Trigger trigger,
            boolean ffmpegAvailable,
            boolean downloadInProgress,
            FfmpegDownloadConsent consent,
            boolean startSharingPromptAlreadyOfferedThisRun,
            boolean addedNonNativeVideo) {
        if (ffmpegAvailable || downloadInProgress) {
            return false;
        }
        return switch (trigger) {
            case START_SHARING -> !startSharingPromptAlreadyOfferedThisRun
                    && consent != FfmpegDownloadConsent.DECLINED;
            case ADDED_NON_NATIVE_VIDEO -> addedNonNativeVideo
                    && consent != FfmpegDownloadConsent.DECLINED;
            case USER_ENABLED_FIX_AUDIO_TOGGLE -> true;
        };
    }
}
