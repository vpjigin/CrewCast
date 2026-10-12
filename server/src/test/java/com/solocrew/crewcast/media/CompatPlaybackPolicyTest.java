package com.solocrew.crewcast.media;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatPlaybackPolicyTest {

    private static ProbedMedia hevcMain() {
        return new ProbedMedia(
                0, "hevc", true, List.of(),
                List.of(),
                new VideoFormat(1920, 1080, 24, "yuv420p", "Main", 8, "", ""));
    }

    @Test
    void hevcNeedsCompat() {
        assertTrue(CompatPlaybackPolicy.videoNeedsCompatTranscode(hevcMain()));
    }

    @Test
    void h264Yuv420p8BitDirect() {
        ProbedMedia media = new ProbedMedia(
                0, "h264", false, List.of(),
                List.of(),
                new VideoFormat(1920, 1080, 24, "yuv420p", "High", 8, "", ""));
        assertFalse(CompatPlaybackPolicy.videoNeedsCompatTranscode(media));
    }

    @Test
    void tenBitH264NeedsCompat() {
        ProbedMedia media = new ProbedMedia(
                0, "h264", false, List.of(),
                List.of(),
                new VideoFormat(1920, 1080, 24, "yuv420p10le", "High 10", 10, "", ""));
        assertTrue(CompatPlaybackPolicy.videoNeedsCompatTranscode(media));
    }

    @Test
    void tvUaAutoEnableWhenHevc() {
        assertTrue(CompatPlaybackPolicy.shouldAutoEnableCompatOnLoad(
                "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0)",
                hevcMain(),
                false,
                true,
                "NEEDED",
                null,
                null,
                null));
    }

    @Test
    void desktopDoesNotAutoEnableSafeH264() {
        ProbedMedia media = new ProbedMedia(
                0, "h264", false, List.of(),
                List.of(),
                new VideoFormat(1280, 720, 24, "yuv420p", "High", 8, "", ""));
        assertFalse(CompatPlaybackPolicy.shouldAutoEnableCompatOnLoad(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X)",
                media,
                false,
                false,
                "NOT_NEEDED",
                true,
                true,
                true));
    }

    @Test
    void autoWhenRiskyAndHostReady() {
        assertTrue(CompatPlaybackPolicy.shouldAutoEnableCompatOnLoad(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X)",
                hevcMain(),
                false,
                true,
                "READY",
                null,
                null,
                null));
    }

    @Test
    void noAutoWhenUserOptedOut() {
        assertFalse(CompatPlaybackPolicy.shouldAutoEnableCompatOnLoad(
                "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0)",
                hevcMain(),
                true,
                true,
                "READY",
                null,
                null,
                null));
    }

    @Test
    void autoWhenDecodeNotSupportedEvenIfNotRisky() {
        ProbedMedia media = new ProbedMedia(
                0, "h264", false, List.of(),
                List.of(),
                new VideoFormat(1280, 720, 24, "yuv420p", "High", 8, "", ""));
        assertTrue(CompatPlaybackPolicy.shouldAutoEnableCompatOnLoad(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X)",
                media,
                false,
                false,
                "NOT_NEEDED",
                false,
                null,
                null));
    }

    @Test
    void autoWhenDecodeSupportedButNotSmooth() {
        assertTrue(CompatPlaybackPolicy.shouldAutoEnableCompatOnLoad(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X)",
                hevcMain(),
                false,
                true,
                "NEEDED",
                true,
                false,
                null));
    }

    @Test
    void clientHevcMain10NotTrustedOnTv() {
        assertFalse(CompatPlaybackPolicy.clientHevcMain10Supported(
                "Mozilla/5.0 (Web0S; Linux/SmartTV)", true));
    }

    @Test
    void isTvLikeUserAgentDetectsRoku() {
        assertTrue(CompatPlaybackPolicy.isTvLikeUserAgent("Roku/DVP-12.0 (12.0.0.4186-AE)"));
    }

    @Test
    void afterErrorRetryOnceForDecodeErrors() {
        assertTrue(CompatPlaybackPolicy.shouldAutoEnableCompatAfterPlaybackError(false, false, 3));
        assertTrue(CompatPlaybackPolicy.shouldAutoEnableCompatAfterPlaybackError(false, false, 4));
        assertFalse(CompatPlaybackPolicy.shouldAutoEnableCompatAfterPlaybackError(true, false, 3));
        assertFalse(CompatPlaybackPolicy.shouldAutoEnableCompatAfterPlaybackError(false, true, 3));
        assertFalse(CompatPlaybackPolicy.shouldAutoEnableCompatAfterPlaybackError(false, false, 2));
    }
}
