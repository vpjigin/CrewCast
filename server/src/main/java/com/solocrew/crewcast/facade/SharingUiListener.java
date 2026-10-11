package com.solocrew.crewcast.facade;

import java.util.List;

public interface SharingUiListener {

    default void onServerReady(int port) {
    }

    default void onSharingChanged(boolean active) {
    }

    default void onTunnelStateChanged(TunnelState state, String detail) {
    }

    default void onTunnelDownloadProgress(int percent, String message) {
    }

    default void onTunnelUrl(String url) {
    }

    default void onTunnelError(String message, boolean retryable) {
    }

    default void onViewersChanged(List<ViewerView> viewers) {
    }
}
