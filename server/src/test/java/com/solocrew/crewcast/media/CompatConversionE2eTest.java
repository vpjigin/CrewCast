package com.solocrew.crewcast.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "server.port=0")
@AutoConfigureMockMvc
@E2eFfmpegRequired
class CompatConversionE2eTest extends CrewcastSpringTestBase {

    private static Path fixture;
    private static String videoId;
    private static boolean hardwareH264;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ShareRegistry shareRegistry;
    @Autowired
    SharingState sharingState;
    @Autowired
    SettingsStore settingsStore;
    @Autowired
    CompatFixService compatFixService;
    @Autowired
    FfmpegManager ffmpegManager;

    @BeforeAll
    static void detectHardware() throws Exception {
        Optional<Path> ffmpeg = FfmpegTestSupport.ffmpegPath();
        assumeTrue(ffmpeg.isPresent());
        H264EncoderDetector detector = new H264EncoderDetector(ffmpeg.get());
        hardwareH264 = detector.resolveEncoder() != null;
    }

    @DynamicPropertySource
    static void fixtures(DynamicPropertyRegistry registry) throws Exception {
        if (!FfmpegTestSupport.ffmpegAndFfprobeAvailable()) {
            return;
        }
        if (fixture == null) {
            fixture = buildTenBitHevcFixture();
        }
        videoId = ShareRegistry.idForPath(fixture);
    }

    @BeforeEach
    void sharingOn() throws Exception {
        assumeTrue(hardwareH264, "No working hardware H.264 encoder in ffmpeg");
        settingsStore.getSettings().setShares(new java.util.ArrayList<>());
        settingsStore.save();
        sharingState.setSharingActive(true);
        AppSettings s = settingsStore.getSettings();
        s.setFixAudioForBrowsers(true);
        s.setRequirePrivateKey(false);
        settingsStore.update(s);
        ffmpegManager.recheck();
        shareRegistry.addFile(fixture);
        videoId = shareRegistry.listVideos().getFirst().id();
    }

    @Test
    void convertsTenBitHevcToH264Yuv420pWithAudio() throws Exception {
        mockMvc.perform(post("/api/videos/" + videoId + "/compat/prepare?audio=0"))
                .andExpect(status().isOk());

        ObjectMapper om = new ObjectMapper();
        for (int i = 0; i < 120; i++) {
            String body = mockMvc.perform(get("/api/videos/" + videoId + "/compat-status?audio=0"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode st = om.readTree(body);
            if ("READY".equals(st.get("status").asText())) {
                break;
            }
            if ("FAILED".equals(st.get("status").asText())) {
                throw new AssertionError("compat failed: " + body);
            }
            TimeUnit.SECONDS.sleep(2);
        }

        Path cached = compatFixService.resolvePlayback(fixture, 0).file().orElseThrow();
        assertTrue(Files.isRegularFile(cached));

        Process p = new ProcessBuilder(
                ffmpegManager.ffprobe().toString(),
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=codec_name,pix_fmt,profile",
                "-of", "json",
                cached.toString())
                .redirectErrorStream(true)
                .start();
        String json = new String(p.getInputStream().readAllBytes());
        p.waitFor(30, TimeUnit.SECONDS);
        JsonNode stream = om.readTree(json).path("streams").get(0);
        assertEquals("h264", stream.path("codec_name").asText());
        assertTrue(stream.path("pix_fmt").asText().startsWith("yuv420p"));

        Process pa = new ProcessBuilder(
                ffmpegManager.ffprobe().toString(),
                "-v", "error",
                "-select_streams", "a:0",
                "-show_entries", "stream=codec_name",
                "-of", "default=noprint_wrappers=1:nokey=1",
                cached.toString())
                .redirectErrorStream(true)
                .start();
        String acodec = new String(pa.getInputStream().readAllBytes()).trim();
        pa.waitFor(10, TimeUnit.SECONDS);
        assertEquals("aac", acodec);
    }

    private static Path buildTenBitHevcFixture() throws Exception {
        Optional<Path> ffmpeg = FfmpegTestSupport.ffmpegPath();
        assumeTrue(ffmpeg.isPresent());
        Path out = Files.createTempDirectory("crewcast-hevc10-").resolve("sample.mkv");
        List<String> cmd = new ArrayList<>(List.of(
                ffmpeg.get().toString(),
                "-y",
                "-f", "lavfi",
                "-i", "sine=frequency=440:duration=1",
                "-f", "lavfi",
                "-i", "color=c=red:s=320x240:r=24:d=1",
                "-pix_fmt", "yuv420p10le",
                "-c:v", "libx265",
                "-t", "1",
                "-c:a", "aac",
                "-shortest",
                out.toString()));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        int code = p.waitFor(120, TimeUnit.SECONDS) ? p.exitValue() : -1;
        assumeTrue(code == 0 && Files.isRegularFile(out) && Files.size(out) > 500,
                "Could not build libx265 10-bit fixture (need libx265 in ffmpeg)");
        return out;
    }
}
