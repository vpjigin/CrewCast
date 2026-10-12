package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.PrepareCompatMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompatStatusMapperTest {

    @Test
    void mapsReadyWhenCacheReady() {
        ProbedMedia media = new ProbedMedia(0, "hevc", true, List.of(), List.of(), VideoFormat.unknown());
        AudioFixStatus st = new AudioFixStatus(AudioFixJobState.NOT_PREPARED, 0, "", "k");
        assertEquals(CompatUiState.READY, CompatStatusMapper.map(
                PrepareCompatMode.NEEDS_COMPAT, media, true, st, true));
    }

    @Test
    void mapsConvertingFromJob() {
        ProbedMedia media = new ProbedMedia(0, "hevc", true, List.of(), List.of(), VideoFormat.unknown());
        AudioFixStatus st = new AudioFixStatus(AudioFixJobState.RUNNING, 43, "Preparing…", "k");
        assertEquals(CompatUiState.CONVERTING, CompatStatusMapper.map(
                PrepareCompatMode.NEEDS_COMPAT, media, true, st, false));
    }

    @Test
    void mapsNotNeededForSafeH264() {
        ProbedMedia media = new ProbedMedia(
                0, "h264", false, List.of(), List.of(),
                new VideoFormat(1280, 720, 24, "yuv420p", "High", 8, "", ""));
        AudioFixStatus st = new AudioFixStatus(AudioFixJobState.NOT_PREPARED, 0, "", "k");
        assertEquals(CompatUiState.NOT_NEEDED, CompatStatusMapper.map(
                PrepareCompatMode.NEEDS_COMPAT, media, true, st, false));
    }

    @Test
    void mapsUnavailableWithoutEncoder() {
        ProbedMedia media = new ProbedMedia(0, "hevc", true, List.of(), List.of(), VideoFormat.unknown());
        AudioFixStatus st = new AudioFixStatus(AudioFixJobState.NOT_PREPARED, 0, "", "k");
        assertEquals(CompatUiState.UNAVAILABLE, CompatStatusMapper.map(
                PrepareCompatMode.NEEDS_COMPAT, media, false, st, false));
    }
}
