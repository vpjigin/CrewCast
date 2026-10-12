package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.PrepareCompatMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Auto-enqueue eligibility (cache skip is enforced in CompatFixService.autoPrepareOne). */
class CompatAutoEnqueueLogicTest {

    @Test
    void offModeNeverAutoEnqueues() {
        ProbedMedia hevc = new ProbedMedia(0, "hevc", true, List.of(), List.of(), VideoFormat.unknown());
        assertFalse(CompatPreparePolicy.shouldAutoPrepare(PrepareCompatMode.OFF, hevc));
    }

    @Test
    void needsCompatSkipsBrowserSafe() {
        ProbedMedia safe = new ProbedMedia(
                0, "h264", false, List.of(), List.of(),
                new VideoFormat(1920, 1080, 24, "yuv420p", "High", 8, "", ""));
        assertFalse(CompatPreparePolicy.shouldAutoPrepare(PrepareCompatMode.NEEDS_COMPAT, safe));
    }

    @Test
    void allVideosEnqueuesSafeH264() {
        ProbedMedia safe = new ProbedMedia(
                0, "h264", false, List.of(), List.of(),
                new VideoFormat(1920, 1080, 24, "yuv420p", "High", 8, "", ""));
        assertTrue(CompatPreparePolicy.shouldAutoPrepare(PrepareCompatMode.ALL_VIDEOS, safe));
    }
}
