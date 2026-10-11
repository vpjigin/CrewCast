package com.solocrew.crewcast.media;

import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SubtitlePipelineTest extends CrewcastSpringTestBase {

    private static SubtitleFixture.Bundle fixture;

    @Autowired
    SubtitleService subtitleService;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SharingState sharingState;

    @BeforeEach
    void sharingOn() {
        sharingState.setSharingActive(true);
    }

    @BeforeAll
    static void build() throws Exception {
        assumeTrue(MediaProbeTest.commandExists("ffmpeg"), "ffmpeg not on PATH");
        fixture = SubtitleFixture.create();
    }

    @Test
    void probeListsEmbeddedAndSidecarTracks() throws Exception {
        ProbedMedia dual = MediaProbe.probe(fixture.embeddedDualMkv());
        assertEquals(2, dual.subtitleTracks().size());
        assertTrue(dual.subtitleTracks().get(0).supported());
        assertEquals("eng", dual.subtitleTracks().get(0).languageTag());
        assertEquals("hin", dual.subtitleTracks().get(1).languageTag());

        var catalog = SubtitleCatalog.build(fixture.sidecarVideo(), MediaProbe.probe(fixture.sidecarVideo()));
        assertTrue(catalog.size() >= 2);
        assertTrue(catalog.stream().anyMatch(t -> t.source() == SubtitleCatalog.Source.EXTERNAL
                && "en".equals(t.language())));
        assertTrue(catalog.stream().anyMatch(t -> t.source() == SubtitleCatalog.Source.EXTERNAL
                && "ml".equals(t.language())));
    }

    @Test
    void bitmapSubtitlesMarkedUnsupported() throws Exception {
        String json = """
                {"streams":[
                  {"index":0,"codec_type":"video","codec_name":"h264"},
                  {"index":1,"codec_type":"subtitle","codec_name":"hdmv_pgs_subtitle","tags":{"language":"eng"}}
                ]}
                """;
        ProbedMedia media = MediaProbe.parse(json);
        assertEquals(1, media.subtitleTracks().size());
        assertFalse(media.subtitleTracks().get(0).supported());
    }

    @Test
    void vttConversionStartsWithWebVttAndCueTimes() throws Exception {
        Path vtt = subtitleService.resolveVtt(fixture.embeddedDualMkv(), 0).orElseThrow();
        String text = Files.readString(vtt, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("WEBVTT"));
        assertTrue(text.contains("-->"));
        assertTrue(text.contains(".") || text.contains("Hello"));

        Path sidecarVtt = subtitleService.resolveVtt(fixture.sidecarVideo(), 0).orElseThrow();
        String side = Files.readString(sidecarVtt, StandardCharsets.UTF_8);
        assertTrue(side.startsWith("WEBVTT"));
        assertTrue(side.contains("Sidecar English"));
    }

    @Test
    void utf16MalayalamSidecarBecomesUtf8() throws Exception {
        var tracks = subtitleService.catalogFor(fixture.sidecarVideo());
        int mlIndex = tracks.stream()
                .filter(t -> "ml".equals(t.language()))
                .findFirst()
                .orElseThrow()
                .index();
        Path vtt = subtitleService.resolveVtt(fixture.sidecarVideo(), mlIndex).orElseThrow();
        String text = Files.readString(vtt, StandardCharsets.UTF_8);
        assertTrue(text.contains("ഹലോ"));
    }

    @Test
    void invalidSubtitleIndexReturns404() throws Exception {
        mockMvc.perform(get("/subtitles/not-a-real-id/0.vtt"))
                .andExpect(status().isNotFound());
    }
}
