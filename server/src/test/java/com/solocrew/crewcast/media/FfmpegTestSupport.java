package com.solocrew.crewcast.media;

import com.solocrew.crewcast.system.CrewcastPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Test-side ffmpeg/ffprobe lookup aligned with {@link FfmpegManager} (system property,
 * {@code crewcast.home}/bin, then PATH unless {@code crewcast.ffmpeg.skipSystemSearch}).
 */
public final class FfmpegTestSupport {

    public static final String FFMPEG_NOT_ON_PATH = "ffmpeg not on PATH";

    private FfmpegTestSupport() {}

    public static boolean ffmpegAndFfprobeAvailable() {
        Optional<Path> ffmpeg = locate("ffmpeg").filter(FfmpegManager::commandWorks);
        Optional<Path> ffprobe = locate("ffprobe").filter(FfmpegManager::commandWorks);
        return ffmpeg.isPresent() && ffprobe.isPresent();
    }

    /** Resolved ffmpeg binary for tests (same search order as {@link FfmpegManager}). */
    public static Optional<Path> ffmpegPath() {
        return locate("ffmpeg").filter(FfmpegManager::commandWorks);
    }

    public static boolean ffmpegDownloadE2eEnabled() {
        return Boolean.getBoolean("crewcast.ffmpeg.e2e");
    }

    private static Optional<Path> locate(String binary) {
        List<Path> candidates = new ArrayList<>();
        String propPath = System.getProperty("crewcast.ffmpeg.path");
        if (propPath != null && !propPath.isBlank()) {
            candidates.add(resolveSibling(propPath, binary));
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
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return binary + ".exe";
        }
        return binary;
    }

    private static boolean skipSystemSearch() {
        return Boolean.parseBoolean(System.getProperty("crewcast.ffmpeg.skipSystemSearch", "false"))
                || Boolean.parseBoolean(System.getProperty("crewcast.ffmpeg.forceDownload", "false"));
    }

    private static boolean isExecutable(Path p) {
        return Files.isRegularFile(p) && Files.isExecutable(p);
    }
}
