package com.solocrew.crewcast.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConversionPublicStartedByTest {

    @Test
    void mapsHostViewerAndSelf() {
        AudioFixStatus host = new AudioFixStatus(
                AudioFixJobState.RUNNING, 10, "", "k",
                null, null, ConversionStartedBy.HOST, "Host PC", null, null);
        assertEquals("HOST", ConversionPublicStartedBy.forClient(host, "tab-1"));

        AudioFixStatus viewer = new AudioFixStatus(
                AudioFixJobState.RUNNING, 10, "", "k",
                null, null, ConversionStartedBy.VIEWER, "Android · Chrome", null, "tab-2");
        assertEquals("VIEWER", ConversionPublicStartedBy.forClient(viewer, "tab-other"));
        assertEquals("SELF", ConversionPublicStartedBy.forClient(viewer, "tab-2"));
    }

    @Test
    void dtoOmitsLabelAndUsesPublicStartedBy() {
        AudioFixStatus st = new AudioFixStatus(
                AudioFixJobState.QUEUED, 0, "", "k",
                null, null, ConversionStartedBy.VIEWER, "secret label", 2, "cid");
        ConversionProgressDto dto = ConversionProgressDto.from(st, "cid");
        assertEquals("SELF", dto.startedBy());
        assertEquals(2, dto.queueAhead());
    }
}
