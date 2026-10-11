package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.SettingsStore;
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

    private final SettingsStore settingsStore;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile Path ffmpegPath;
    private volatile Path ffprobePath;
    private volatile boolean available;

    public FfmpegManager(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

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
            log.warn("Crewcast ffmpeg not found — audio fix disabled (install or download ffmpeg, then Re-check)");
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
            return "brew install ffmpeg, or Download ffmpeg in Settings";
        }
        if (os.contains("win")) {
            return "winget install ffmpeg, or Download ffmpeg in Settings";
        }
        return "apt install ffmpeg, or Download ffmpeg in Settings";
    }

    private Optional<Path> locate(String binary) {
        List<Path> candidates = new ArrayList<>();
        String propPath = System.getProperty("crewcast.ffmpeg.path");
        if (propPath != null && !propPath.isBlank()) {
            candidates.add(resolveSibling(propPath, binary));
        }
        AppSettings settings = settingsStore.getSettings();
        if (settings.getFfmpegBinaryPath() != null && !settings.getFfmpegBinaryPath().isBlank()) {
            candidates.add(resolveSibling(settings.getFfmpegBinaryPath(), binary));
        }
        candidates.add(CrewcastPaths.binDir().resolve(binaryName(binary)));
        if (!skipSystemSearch()) {
            candidates.add(Path.of("/opt/homebrew/bin", binaryName(binary)));
            candidates.add(Path.of("/usr/local/bin", binaryName(binary)));
            String pathEnv = System.getenv("PATH");
            if (pathEnv != null) {
                for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
                    if (!dir.isBlank()) {
                        candidates.add(Path.of(dir.trim(), binaryName(binary)));
                    }
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

    private static Path resolveSibling(String configured, String binary) {
        Path p = Path.of(configured.trim());
        if (Files.isDirectory(p)) {
            return p.resolve(binaryName(binary));
        }
        if (binary.equals("ffmpeg")) {
            return p;
        }
        Path parent = p.getParent();
        String name = p.getFileName().toString();
        String probe = name.replace("ffmpeg", "ffprobe");
        return parent != null ? parent.resolve(probe) : Path.of(probe);
    }

    private static String binaryName(String binary) {
        if (isWindows()) {
            return binary + ".exe";
        }
        return binary;
    }

    private static boolean skipSystemSearch() {
        return Boolean.parseBoolean(System.getProperty("crewcast.ffmpeg.skipSystemSearch", "false"))
                || Boolean.parseBoolean(System.getProperty("crewcast.ffmpeg.forceDownload", "false"));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
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
