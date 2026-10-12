package com.solocrew.crewcast.media;

/** Host and web player status for TV-safe H.264 cache (default audio track). */
public enum CompatUiState {
    NOT_NEEDED,
    NEEDED,
    QUEUED,
    CONVERTING,
    READY,
    UNAVAILABLE,
    FAILED
}
