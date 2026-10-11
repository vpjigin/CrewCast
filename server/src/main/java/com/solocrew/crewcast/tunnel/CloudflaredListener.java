package com.solocrew.crewcast.tunnel;

public interface CloudflaredListener {

    void onDownloadProgress(int percent, String message);

    void onTunnelUrl(String url);

    void onStateChanged(CloudflaredManager.State state, String detail);

    void onError(String message, boolean retryable);

    default void onError(String message, boolean retryable, com.solocrew.crewcast.net.DownloadFailureDetails details) {
        onError(message, retryable);
    }
}
