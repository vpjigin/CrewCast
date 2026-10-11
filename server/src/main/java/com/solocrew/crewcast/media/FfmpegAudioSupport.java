package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.FfmpegDownloadConsent;

/** Persisted settings changes for the ffmpeg / fix-audio consent flow. */
public final class FfmpegAudioSupport {

    private FfmpegAudioSupport() {
    }

    public static void applyUserDeclined(AppSettings settings) {
        settings.setFixAudioForBrowsers(false);
        settings.setFfmpegDownloadConsent(FfmpegDownloadConsent.DECLINED);
    }

    public static void applyUserChoseDownload(AppSettings settings) {
        settings.setFixAudioForBrowsers(true);
        settings.setFfmpegDownloadConsent(FfmpegDownloadConsent.ALLOWED);
    }

    public static void applyManualFfmpegConfigured(AppSettings settings) {
        settings.setFixAudioForBrowsers(true);
        settings.setFfmpegDownloadConsent(FfmpegDownloadConsent.MANUAL);
    }

    public static void applyDownloadFailed(AppSettings settings) {
        settings.setFixAudioForBrowsers(false);
    }
}
