package com.solocrew.crewcast.media;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareVideoDetailsMetaTest {

    @Test
    void formatsSizeVideoAndAudioCodec() {
        ProbedMedia media = new ProbedMedia(
                0,
                "hevc",
                true,
                List.of(new AudioStreamInfo(1, 0, "ac3", "eng", "", 6, "5.1")),
                List.of(),
                new VideoFormat(1920, 1080, 24.0, "yuv420p10le", "main10", 10, "", ""));
        assertEquals("HEVC 10-bit", ShareVideoDetailsMeta.formatVideo(media));
        assertEquals("AC3", ShareVideoDetailsMeta.formatAudio(media, 0));
    }

    @Test
    void h264EightBitLabel() {
        ProbedMedia media = new ProbedMedia(
                0,
                "h264",
                false,
                List.of(new AudioStreamInfo(1, 0, "aac", "eng", "", 2, "stereo")),
                List.of(),
                new VideoFormat(1920, 1080, 24.0, "yuv420p", "high", 8, "", ""));
        assertTrue(ShareVideoDetailsMeta.formatVideo(media).startsWith("H.264"));
    }
}
