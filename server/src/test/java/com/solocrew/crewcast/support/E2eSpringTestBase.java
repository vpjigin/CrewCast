package com.solocrew.crewcast.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

/** Spring tests that need real ffmpeg on PATH (see {@code @E2eFfmpegRequired}). */
public abstract class E2eSpringTestBase extends CrewcastSpringTestBase {

    @DynamicPropertySource
    static void allowSystemFfmpegDiscovery(DynamicPropertyRegistry registry) {
        registry.add("crewcast.ffmpeg.disable-discovery", () -> "false");
    }

    @BeforeEach
    void preferSystemFfmpegOverStubs() throws IOException {
        FfmpegTestBinaries.removeStubTools(isolatedHome());
        ffmpegManager.recheck();
    }
}
