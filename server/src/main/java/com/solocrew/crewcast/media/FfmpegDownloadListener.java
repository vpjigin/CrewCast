package com.solocrew.crewcast.media;

public interface FfmpegDownloadListener {

    void onFfmpegDownloadProgress(int percent, String message);

    default void onFfmpegDownloadFinished(boolean success, String message) {
    }
}
