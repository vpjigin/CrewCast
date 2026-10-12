package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.PrepareCompatMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatPreparePolicyTest {

    private static ProbedMedia hevc() {
        return new ProbedMedia(0, "hevc", true, List.of(), List.of(), VideoFormat.unknown());
    }

    private static ProbedMedia h264Safe() {
        return new ProbedMedia(
                0, "h264", false, List.of(),
                List.of(),
                new VideoFormat(1920, 1080, 24, "yuv420p", "High", 8, "", ""));
    }

    @Test
    void offSkipsAutoPrepare() {
        assertFalse(CompatPreparePolicy.shouldAutoPrepare(PrepareCompatMode.OFF, hevc()));
    }

    @Test
    void needsCompatModeOnlyHevc() {
        assertTrue(CompatPreparePolicy.shouldAutoPrepare(PrepareCompatMode.NEEDS_COMPAT, hevc()));
        assertFalse(CompatPreparePolicy.shouldAutoPrepare(PrepareCompatMode.NEEDS_COMPAT, h264Safe()));
    }

    @Test
    void allVideosModeIncludesSafeH264() {
        assertTrue(CompatPreparePolicy.shouldAutoPrepare(PrepareCompatMode.ALL_VIDEOS, h264Safe()));
    }

    @Test
    void notNeededWhenSafeAndNeedsCompatMode() {
        assertTrue(CompatPreparePolicy.videoMarkedNotNeeded(PrepareCompatMode.NEEDS_COMPAT, h264Safe()));
    }

}
