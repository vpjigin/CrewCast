package com.solocrew.crewcast.media;

import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MediaProbeTest extends CrewcastSpringTestBase {

    private static Path fixture;

    @BeforeAll
    static void buildFixture() throws Exception {
        assumeTrue(commandExists("ffmpeg"), "ffmpeg not on PATH");
        fixture = CoverArtFixture.create();
    }

    static boolean commandExists(String name) {
        try {
            Process p = new ProcessBuilder(name, "-version").redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void ignoresAttachedPicForMainVideo() throws Exception {
        ProbedMedia media = MediaProbe.probe(fixture);
        assertEquals(0, media.mainVideoAbsoluteIndex());
        assertEquals("hevc", media.mainVideoCodec());
        assertTrue(media.hevcInBrowserWarning());
        assertEquals(2, media.audioTracks().size());
        assertEquals("hin", media.audioTracks().get(0).languageTag());
        assertEquals("mal", media.audioTracks().get(1).languageTag());
        assertEquals(0, media.audioTracks().get(0).audioIndex());
        assertEquals(1, media.audioTracks().get(1).audioIndex());
    }

    @Test
    void parseJsonSkipsAttachedPicStream() throws Exception {
        String json = """
                {
                  "streams": [
                    {"index":0,"codec_type":"video","codec_name":"hevc","disposition":{"attached_pic":0}},
                    {"index":1,"codec_type":"audio","codec_name":"eac3","channels":6,"channel_layout":"5.1(side)",
                     "tags":{"language":"hin"}},
                    {"index":2,"codec_type":"audio","codec_name":"eac3","channels":6,"channel_layout":"5.1(side)",
                     "tags":{"language":"mal"}},
                    {"index":3,"codec_type":"video","codec_name":"mjpeg","width":1,"height":1,
                     "disposition":{"attached_pic":1}}
                  ]
                }
                """;
        ProbedMedia media = MediaProbe.parse(json);
        assertEquals(0, media.mainVideoAbsoluteIndex());
        assertFalse(media.audioTracks().isEmpty());
    }

    @Test
    void preferredLanguageSelectsMalayalamOrHindi() throws Exception {
        ProbedMedia media = MediaProbe.probe(fixture);
        assertEquals(1, AudioTrackSelector.selectAudioIndex(media, null, "ml"));
        assertEquals(0, AudioTrackSelector.selectAudioIndex(media, null, "hi"));
    }

    @Test
    void ffmpegMapsAbsoluteVideoAndRelativeAudio() throws Exception {
        ProbedMedia media = MediaProbe.probe(fixture);
        int mal = AudioTrackSelector.selectAudioIndex(media, null, "ml");
        var cmd = FfmpegTranscodePlanner.buildTranscodeCommand(
                Path.of("ffmpeg"), fixture, media, mal, fixture.getParent().resolve("out.part"));
        assertTrue(cmd.contains("-map"));
        assertTrue(cmd.contains("0:0"));
        assertTrue(cmd.contains("0:a:1"));
        assertFalse(String.join(" ", cmd).contains("0:3"));
        assertTrue(String.join(" ", cmd).contains(FfmpegTranscodePlanner.DOWNMIX_51_FILTER));
        assertTrue(String.join(" ", cmd).contains("alimiter=limit=0.95"));
    }

    @Test
    void audioQueryOverridesPreferredLanguage() throws Exception {
        ProbedMedia media = MediaProbe.probe(fixture);
        assertEquals(0, AudioTrackSelector.selectAudioIndex(media, 0, "ml"));
        assertEquals(1, AudioTrackSelector.selectAudioIndex(media, 1, "hi"));
    }
}
