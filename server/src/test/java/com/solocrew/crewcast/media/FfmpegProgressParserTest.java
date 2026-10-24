package com.solocrew.crewcast.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FfmpegProgressParserTest {

    @Test
    void parsesProgressSpeedAndEta() {
        FfmpegProgressParser.Snapshot s1 =
                FfmpegProgressParser.applyLine("out_time_us=30000000", 100.0, 0);
        assertEquals(30, s1.progress);

        FfmpegProgressParser.Snapshot s2 =
                FfmpegProgressParser.applyLine("speed=2.0x", 100.0, s1.progress);
        assertEquals(2.0, s2.speed);
        assertNotNull(s2.etaSeconds);
        assertEquals(35, s2.etaSeconds);
    }

    @Test
    void parseSpeedHandlesPlainNumber() {
        assertEquals(1.5, FfmpegProgressParser.parseSpeed("speed=1.5"));
    }
}
