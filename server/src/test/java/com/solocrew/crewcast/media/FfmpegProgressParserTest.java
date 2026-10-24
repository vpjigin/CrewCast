package com.solocrew.crewcast.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegProgressParserTest {

    /** ~2h 1m feature (7260 s), ~40% encoded — sample µs values from ffmpeg -progress. */
    private static final double FILM_DURATION_SEC = 7260.0;

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
    void outTimeMsIsMicrosecondsNotMilliseconds() {
        long microsAt40Pct = (long) (FILM_DURATION_SEC * 0.40 * 1_000_000.0);
        FfmpegProgressParser.ProgressState state = new FfmpegProgressParser.ProgressState(0);
        FfmpegProgressParser.Snapshot s = FfmpegProgressParser.applyLine(
                "out_time_ms=" + microsAt40Pct, FILM_DURATION_SEC, state);
        assertEquals(40, s.progress);
        assertFalse(s.finishing);
    }

    @Test
    void outTimeUsAndOutTimeHmsAgreeAt40Percent() {
        FfmpegProgressParser.ProgressState state = new FfmpegProgressParser.ProgressState(0);
        long microsAt40Pct = (long) (FILM_DURATION_SEC * 0.40 * 1_000_000.0);
        FfmpegProgressParser.applyLine("out_time_us=" + microsAt40Pct, FILM_DURATION_SEC, state);
        assertEquals(40, state.progress);

        state = new FfmpegProgressParser.ProgressState(0);
        FfmpegProgressParser.applyLine("out_time=00:48:24.000000", FILM_DURATION_SEC, state);
        assertEquals(40, state.progress);
    }

    @Test
    void realSampleBlockProgressAndFinishing() {
        FfmpegProgressParser.ProgressState state = new FfmpegProgressParser.ProgressState(0);
        long microsAt40Pct = (long) (FILM_DURATION_SEC * 0.40 * 1_000_000.0);
        String[] block = {
                "frame=1000",
                "out_time_us=" + microsAt40Pct,
                "out_time_ms=" + microsAt40Pct,
                "out_time=00:48:24.000000",
                "progress=continue",
                "speed=1.03x",
                "",
                "progress=end",
        };
        for (String line : block) {
            if (line.isEmpty()) {
                continue;
            }
            FfmpegProgressParser.applyLine(line, FILM_DURATION_SEC, state);
        }
        assertEquals(1.03, state.speed, 0.01);
        assertTrue(state.finishing);
        assertEquals(99, state.progress);
    }

    @Test
    void progressEndClampsTo99UntilJobCompletes() {
        FfmpegProgressParser.Snapshot end =
                FfmpegProgressParser.applyLine("progress=end", 100.0, 50);
        assertEquals(99, end.progress);
        assertTrue(end.finishing);
    }

    @Test
    void parseSpeedHandlesPlainNumber() {
        assertEquals(1.5, FfmpegProgressParser.parseSpeed("speed=1.5"));
    }
}
