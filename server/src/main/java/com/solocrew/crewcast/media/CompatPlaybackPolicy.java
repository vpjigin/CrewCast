package com.solocrew.crewcast.media;

import java.util.Locale;
import java.util.Set;

/** Server-side rules for when a TV-safe H.264 transcode is recommended or required. */
public final class CompatPlaybackPolicy {

    private static final Set<String> TV_UA_HINTS = Set.of(
            "smart-tv", "smarttv", "tizen", "web0s", "webos", "hbbtv", "netcast",
            "viera", "bravia", "philipstv", "appletv", "googletv", "android tv",
            "crkey", "aft", "silk/", "roku", "dlnadoc", "tv safari");

    private CompatPlaybackPolicy() {
    }

    public static boolean videoNeedsCompatTranscode(ProbedMedia media) {
        if (media == null) {
            return true;
        }
        String codec = safe(media.mainVideoCodec());
        VideoFormat v = media.video() != null ? media.video() : VideoFormat.unknown();
        if (codec.isEmpty()) {
            return true;
        }
        if (MediaProbe.isHevcCodec(codec)) {
            return true;
        }
        if (v.bitDepth() > 8) {
            return true;
        }
        String pix = safe(v.pixFmt());
        if (pix.contains("10") || pix.contains("12") || pix.contains("p010") || pix.contains("p016")) {
            return true;
        }
        if (!codec.equals("h264") && !codec.equals("avc1")) {
            return true;
        }
        if (!pix.isEmpty() && !pix.startsWith("yuv420p") && !pix.equals("yuvj420p")) {
            return true;
        }
        String profile = safe(v.profile());
        if (profile.contains("high 10") || profile.contains("high10")) {
            return true;
        }
        return false;
    }

    public static boolean isTvLikeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        for (String hint : TV_UA_HINTS) {
            if (ua.contains(hint)) {
                return true;
            }
        }
        return ua.contains(" tv") || ua.endsWith(" tv");
    }

    public static boolean clientHevcMain10Supported(String userAgent, Boolean mediaCapabilitiesHevcMain10) {
        if (isTvLikeUserAgent(userAgent)) {
            return false;
        }
        return Boolean.TRUE.equals(mediaCapabilitiesHevcMain10);
    }

    /**
     * Client-side auto-enable decision (mirrored in player-compat.js).
     *
     * @param userOptedOutPerVideo user unchecked compatibility for this file (localStorage)
     * @param risky                server says a TV-safe transcode is recommended
     * @param compatUiState        {@link CompatUiState} name, e.g. READY
     * @param decodeSupported      null if unknown (no MediaCapabilities / canPlayType)
     * @param decodeSmooth         null if unknown
     */
    public static boolean shouldAutoEnableCompatOnLoad(
            String userAgent,
            ProbedMedia media,
            boolean userOptedOutPerVideo,
            boolean risky,
            String compatUiState,
            Boolean decodeSupported,
            Boolean decodeSmooth,
            Boolean mediaCapabilitiesHevcMain10) {
        if (userOptedOutPerVideo) {
            return false;
        }
        if (!risky) {
            if (Boolean.FALSE.equals(decodeSupported)) {
                return true;
            }
            if (Boolean.TRUE.equals(decodeSupported) && Boolean.FALSE.equals(decodeSmooth)) {
                return true;
            }
            return false;
        }
        if (isTvLikeUserAgent(userAgent)) {
            return true;
        }
        if ("READY".equalsIgnoreCase(safe(compatUiState))) {
            return true;
        }
        if (Boolean.FALSE.equals(decodeSupported)) {
            return true;
        }
        if (Boolean.TRUE.equals(decodeSupported) && Boolean.FALSE.equals(decodeSmooth)) {
            return true;
        }
        if (MediaProbe.isHevcCodec(media.mainVideoCodec()) && bitDepthOf(media) > 8) {
            return !clientHevcMain10Supported(userAgent, mediaCapabilitiesHevcMain10);
        }
        return false;
    }

    /** After a decode/src-not-supported error, retry once with compatibility mode. */
    public static boolean shouldAutoEnableCompatAfterPlaybackError(
            boolean userOptedOutPerVideo,
            boolean alreadyAutoRetriedThisPage,
            int mediaErrorCode) {
        if (userOptedOutPerVideo || alreadyAutoRetriedThisPage) {
            return false;
        }
        return mediaErrorCode == 3 || mediaErrorCode == 4;
    }

    private static int bitDepthOf(ProbedMedia media) {
        return media.video() != null ? media.video().bitDepth() : 8;
    }

    private static String safe(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
