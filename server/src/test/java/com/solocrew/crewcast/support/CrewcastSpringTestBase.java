package com.solocrew.crewcast.support;

import com.solocrew.crewcast.media.AudioFixService;
import com.solocrew.crewcast.media.CompatFixService;
import com.solocrew.crewcast.media.FfmpegManager;
import com.solocrew.crewcast.system.CrewcastWriteGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Ensures {@code crewcast.home} is isolated before the Spring context starts. */
public abstract class CrewcastSpringTestBase {

    static final Path ISOLATED_HOME;

    static {
        try {
            ISOLATED_HOME = Files.createTempDirectory("crewcast-test-home");
            System.setProperty("crewcast.home", ISOLATED_HOME.toString());
            FfmpegTestBinaries.installStubTools(ISOLATED_HOME);
            CrewcastWriteGuard.setEnabled(true);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void crewcastHome(DynamicPropertyRegistry registry) {
        registry.add("crewcast.home", () -> ISOLATED_HOME.toString());
        registry.add("crewcast.ffmpeg.disable-discovery", () -> "true");
    }

    public static Path isolatedHome() {
        return ISOLATED_HOME;
    }

    @Autowired(required = false)
    protected FfmpegManager ffmpegManager;

    @Autowired(required = false)
    private CompatFixService compatFixService;

    @Autowired(required = false)
    private AudioFixService audioFixService;

    @BeforeEach
    void pinIsolatedHomeAndStubFfmpeg() throws IOException {
        System.setProperty("crewcast.home", ISOLATED_HOME.toString());
        FfmpegTestBinaries.installStubTools(ISOLATED_HOME);
        if (ffmpegManager != null) {
            ffmpegManager.recheck();
        }
    }

    @AfterEach
    void stopMediaWorkersBeforeStubRewrite() throws InterruptedException {
        if (compatFixService != null) {
            compatFixService.cancelAll(true);
            compatFixService.awaitWorkerIdle(5, TimeUnit.SECONDS);
        }
        if (audioFixService != null) {
            audioFixService.cancelAllJobs(true);
            audioFixService.awaitWorkerIdle(5, TimeUnit.SECONDS);
        }
    }
}
