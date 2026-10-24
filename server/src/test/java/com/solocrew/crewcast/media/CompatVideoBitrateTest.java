package com.solocrew.crewcast.media;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompatVideoBitrateTest {

    @Test
    void tiersByOutputHeightWhenNoSourceBitrate() {
        ProbedMedia hd = mediaWithHeight(1080, "hevc");
        assertEquals(5000, CompatVideoBitrate.targetKbps(hd, 1080, 0));
        ProbedMedia sd = mediaWithHeight(480, "hevc");
        assertEquals(1500, CompatVideoBitrate.targetKbps(sd, 1080, 0));
        ProbedMedia hd720 = mediaWithHeight(720, "hevc");
        assertEquals(3000, CompatVideoBitrate.targetKbps(hd720, 1080, 0));
    }

    @Test
    void hevcSourceCappedAt160PercentOverallBitrate() {
        ProbedMedia hd = mediaWithHeight(1080, "hevc");
        assertEquals(1648, CompatVideoBitrate.targetKbps(hd, 1080, 1_030_000L));
    }

    @Test
    void h264SourceCappedAt100PercentOverallBitrate() {
        ProbedMedia hd = mediaWithHeight(1080, "h264");
        assertEquals(1030, CompatVideoBitrate.targetKbps(hd, 1080, 1_030_000L));
    }

    @Test
    void minFloor720p() {
        ProbedMedia sd = mediaWithHeight(720, "hevc");
        assertEquals(1000, CompatVideoBitrate.targetKbps(sd, 1080, 100_000L));
    }

    @Test
    void minFloor480p() {
        ProbedMedia sd = mediaWithHeight(480, "hevc");
        assertEquals(600, CompatVideoBitrate.targetKbps(sd, 1080, 100_000L));
    }

    private static ProbedMedia mediaWithHeight(int height, String codec) {
        return new ProbedMedia(
                0,
                codec,
                true,
                List.of(),
                List.of(),
                new VideoFormat(1920, height, 24.0, "yuv420p", "high", 8, "", ""));
    }
}
