package com.solocrew.crewcast.settings;

/** User choice for optional ffmpeg download (persisted so we do not re-prompt). */
public enum FfmpegDownloadConsent {
    /** User has not chosen yet — desktop may show the consent dialog once. */
    UNSET,
    /** User agreed to download from the allow-listed host. */
    ALLOWED,
    /** User chose “Not now” — do not auto-prompt again. */
    DECLINED,
    /** User chose an existing binary path (see {@link AppSettings#getFfmpegBinaryPath()}). */
    MANUAL
}
