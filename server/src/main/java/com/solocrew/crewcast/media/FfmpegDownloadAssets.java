package com.solocrew.crewcast.media;

import com.solocrew.crewcast.tunnel.CloudflaredPlatform;

/** Maps host CPU/OS to allow-listed download artifacts (see docs/FFMPEG-SOURCES.md). */
public final class FfmpegDownloadAssets {

    public enum Provider {
        BTBN_GITHUB,
        MARTIN_RIEDL_MACOS
    }

    public record Plan(
            Provider provider,
            String hostLabel,
            String sourceHost,
            long estimatedBytes,
            String btbNArchiveName,
            String macOsCpu) {

        /** Short host name shown in the consent dialog. */
        public String consentSourceHost() {
            return sourceHost;
        }
    }

    private FfmpegDownloadAssets() {
    }

    public static Plan planFor(CloudflaredPlatform.Info platform) {
        return switch (platform.os()) {
            case WINDOWS -> new Plan(
                    Provider.BTBN_GITHUB,
                    "GitHub (BtbN FFmpeg Builds)",
                    "github.com",
                    180L * 1024 * 1024,
                    "ffmpeg-master-latest-win64-lgpl.zip",
                    null);
            case LINUX -> platform.arch() == CloudflaredPlatform.CpuArch.ARM64
                    ? new Plan(
                            Provider.BTBN_GITHUB,
                            "GitHub (BtbN FFmpeg Builds)",
                            "github.com",
                            120L * 1024 * 1024,
                            "ffmpeg-master-latest-linuxarm64-lgpl.tar.xz",
                            null)
                    : new Plan(
                            Provider.BTBN_GITHUB,
                            "GitHub (BtbN FFmpeg Builds)",
                            "github.com",
                            140L * 1024 * 1024,
                            "ffmpeg-master-latest-linux64-lgpl.tar.xz",
                            null);
            case DARWIN -> new Plan(
                    Provider.MARTIN_RIEDL_MACOS,
                    "ffmpeg.martin-riedl.de",
                    "ffmpeg.martin-riedl.de",
                    55L * 1024 * 1024,
                    null,
                    platform.arch() == CloudflaredPlatform.CpuArch.ARM64 ? "arm64" : "amd64");
            case OTHER -> new Plan(
                    Provider.BTBN_GITHUB,
                    "GitHub (BtbN FFmpeg Builds)",
                    "github.com",
                    140L * 1024 * 1024,
                    "ffmpeg-master-latest-linux64-lgpl.tar.xz",
                    null);
        };
    }

    public static String formatEstimatedSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        return (bytes / (1024 * 1024)) + " MB";
    }
}
