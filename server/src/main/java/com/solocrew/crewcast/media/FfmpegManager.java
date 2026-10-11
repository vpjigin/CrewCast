package com.solocrew.crewcast.media;

import com.solocrew.crewcast.system.CrewcastPaths;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@Component
public class FfmpegManager {

    private static final Logger log = LoggerFactory.getLogger(FfmpegManager.class);

    public interface Listener {
        void onFfmpegAvailabilityChanged(boolean available);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile Path ffmpegPath;
    private volatile Path ffprobePath;
    private volatile boolean available;

    @PostConstruct
    void init() {
        recheck();
    }

    public synchronized void recheck() {
        Optional<Path> ffmpeg = locate("ffmpeg");
        Optional<Path> ffprobe = locate("ffprobe");
        available = ffmpeg.isPresent() && ffprobe.isPresent();
        ffmpegPath = ffmpeg.orElse(null);
        ffprobePath = ffprobe.orElse(null);
        if (available) {
            log.info("Crewcast ffmpeg: {} | ffprobe: {}", ffmpegPath, ffprobePath);
        } else {
            log.warn("Crewcast ffmpeg not found — audio fix disabled (install ffmpeg and use Re-check in settings)");
        }
        for (Listener l : listeners) {
            l.onFfmpegAvailabilityChanged(available);
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
        listener.onFfmpegAvailabilityChanged(available);
    }

    public boolean isAvailable() {
        return available;
    }

    public Path ffmpeg() {
        return ffmpegPath;
    }

    public Path ffprobe() {
        return ffprobePath;
    }

    public static String installHint() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return "brew install ffmpeg";
        }
        if (os.contains("win")) {
            return "winget install ffmpeg";
        }
        return "apt install ffmpeg";
    }

    private Optional<Path> locate(String binary) {
        List<Path> candidates = new ArrayList<>();
        String settingsPath = System.getProperty("crewcast.ffmpeg.path");
        if (settingsPath != null && !settingsPath.isBlank()) {
            candidates.add(Path.of(settingsPath));
        }
        String home = System.getProperty("user.home");
        if (home != null) {
            candidates.add(CrewcastPaths.binDir().resolve(binary));
        }
        candidates.add(Path.of("/opt/homebrew/bin", binary));
        candidates.add(Path.of("/usr/local/bin", binary));
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
                if (!dir.isBlank()) {
                    candidates.add(Path.of(dir.trim(), binary));
                }
            }
        }
        for (Path p : candidates) {
            if (isExecutable(p)) {
                return Optional.of(p.toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }

    private static boolean isExecutable(Path p) {
        return Files.isRegularFile(p) && Files.isExecutable(p);
    }

    static boolean commandWorks(Path binary) {
        try {
            Process p = new ProcessBuilder(binary.toString(), "-version")
                    .redirectErrorStream(true)
                    .start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
