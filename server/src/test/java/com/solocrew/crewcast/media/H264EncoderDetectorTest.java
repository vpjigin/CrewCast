package com.solocrew.crewcast.media;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class H264EncoderDetectorTest {

    @Test
    void parseEncodersOutputFindsHardwareAndSoftware() {
        String sample = """
                Encoders:
                 V..... h264_videotoolbox    VideoToolbox H.264 Encoder
                 V..... libx264              libx264 H.264 / AVC / MPEG-4 AVC
                 V..... hevc_videotoolbox    VideoToolbox H.265 Encoder
                 V..... libopenh264          OpenH264 H.264
                """;
        Set<String> enc = H264EncoderDetector.parseEncodersOutput(sample);
        assertTrue(enc.contains("h264_videotoolbox"));
        assertTrue(enc.contains("libx264"));
        assertTrue(enc.contains("libopenh264"));
        assertEquals(3, enc.size());
    }

    @Test
    void parseEncodersOutputIgnoresNonVideoLines() {
        String sample = " A..... aac                  AAC (Advanced Audio Coding)\n"
                + " V..... h264_nvenc           NVIDIA NVENC H.264 encoder\n";
        Set<String> enc = H264EncoderDetector.parseEncodersOutput(sample);
        assertEquals(Set.of("h264_nvenc"), enc);
    }

    @Test
    void clearCacheForTests() {
        H264EncoderDetector d = new H264EncoderDetector(Path.of("/nonexistent/ffmpeg"));
        d.clearCacheForTests();
    }
}
