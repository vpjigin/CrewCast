package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.ShareEntry;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.system.CrewcastPaths;
import com.solocrew.crewcast.system.CrewcastWriteGuard;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class SubtitleService {

    private static final Logger log = LoggerFactory.getLogger(SubtitleService.class);
    private static final long CONVERT_TIMEOUT_SEC = 30;
    private static final long MAX_VTT_BYTES = 5L * 1024 * 1024;

    private final FfmpegManager ffmpegManager;
    private final AudioFixService audioFixService;
    private final ShareRegistry shareRegistry;

    private final Map<String, Object> convertLocks = new ConcurrentHashMap<>();

    public SubtitleService(FfmpegManager ffmpegManager, AudioFixService audioFixService, ShareRegistry shareRegistry) {
        this.ffmpegManager = ffmpegManager;
        this.audioFixService = audioFixService;
        this.shareRegistry = shareRegistry;
    }

    @PostConstruct
    void init() throws IOException {
        Path dir = CrewcastPaths.subtitleCacheDir();
        CrewcastWriteGuard.check(dir);
        Files.createDirectories(dir);
    }

    public List<SubtitleCatalog.Track> catalogFor(Path video) throws IOException, InterruptedException {
        ProbedMedia media = audioFixService.probe(video);
        return SubtitleCatalog.build(video, media);
    }

    public List<SubtitleDto> listForApi(Path video) throws IOException, InterruptedException {
        return catalogFor(video).stream().map(this::toDto).toList();
    }

    public Optional<Path> resolveVtt(Path video, int trackIndex) throws IOException, InterruptedException {
        List<SubtitleCatalog.Track> tracks = catalogFor(video);
        if (trackIndex < 0 || trackIndex >= tracks.size()) {
            return Optional.empty();
        }
        SubtitleCatalog.Track track = tracks.get(trackIndex);
        if (!track.supported()) {
            return Optional.empty();
        }
        Path cache = cachePath(video, track);
        if (Files.isRegularFile(cache) && Files.size(cache) <= MAX_VTT_BYTES) {
            return Optional.of(cache);
        }
        Object lock = convertLocks.computeIfAbsent(cache.toString(), k -> new Object());
        synchronized (lock) {
            if (Files.isRegularFile(cache) && Files.size(cache) <= MAX_VTT_BYTES) {
                return Optional.of(cache);
            }
            convertToVtt(video, track, cache);
        }
        if (!Files.isRegularFile(cache)) {
            return Optional.empty();
        }
        long size = Files.size(cache);
        if (size > MAX_VTT_BYTES) {
            Files.deleteIfExists(cache);
            log.warn("Subtitle cache over size cap ({} bytes) for {}", size, video.getFileName());
            return Optional.empty();
        }
        return Optional.of(cache);
    }

    public String summarizeForShare(ShareEntry entry) {
        try {
            if (entry.getKind() == ShareEntry.Kind.FILE) {
                return summarizeVideo(Path.of(entry.getPath()));
            }
            String best = null;
            for (Path v : shareRegistry.listVideosUnder(Path.of(entry.getPath()))) {
                String line = summarizeVideo(v);
                if (line != null && (best == null || line.contains("embedded"))) {
                    best = line;
                }
            }
            return best;
        } catch (Exception ex) {
            return null;
        }
    }

    private String summarizeVideo(Path videoPath) throws IOException, InterruptedException {
        for (SubtitleCatalog.Track t : catalogFor(videoPath)) {
            if (!t.supported()) {
                continue;
            }
            String src = t.source() == SubtitleCatalog.Source.EMBEDDED ? "embedded" : "external";
            return "Subtitles: " + t.languageName() + " (" + src + ")";
        }
        return null;
    }

    private SubtitleDto toDto(SubtitleCatalog.Track t) {
        String src = t.source() == SubtitleCatalog.Source.EMBEDDED ? "embedded" : "external";
        return new SubtitleDto(t.index(), t.language(), t.languageName(), t.label(), src, t.supported());
    }

    private Path cachePath(Path video, SubtitleCatalog.Track track) throws IOException {
        return CrewcastPaths.subtitleCacheDir().resolve(SubtitleCacheKeys.cacheFileName(video, track));
    }

    private void convertToVtt(Path video, SubtitleCatalog.Track track, Path out) throws IOException, InterruptedException {
        CrewcastWriteGuard.check(out);
        Files.createDirectories(out.getParent());
        Path work = out.resolveSibling(out.getFileName().toString().replace(".vtt", ".work.vtt"));
        Files.deleteIfExists(work);

        if (track.source() == SubtitleCatalog.Source.EMBEDDED) {
            convertEmbedded(video, track.embeddedSubtitleIndex(), work);
        } else {
            convertExternal(track.externalPath(), work);
        }

        if (!Files.isRegularFile(work)) {
            throw new IOException("Subtitle conversion produced no output");
        }
        long size = Files.size(work);
        if (size > MAX_VTT_BYTES) {
            Files.deleteIfExists(work);
            throw new IOException("Subtitle output exceeds size cap");
        }
        Files.move(work, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void convertEmbedded(Path video, int subtitleIndex, Path out) throws IOException, InterruptedException {
        if (!ffmpegManager.isAvailable()) {
            throw new IOException("ffmpeg not available for subtitle extraction");
        }
        List<String> cmd = List.of(
                ffmpegManager.ffmpeg().toString(),
                "-y", "-loglevel", "error",
                "-i", video.toAbsolutePath().toString(),
                "-map", "0:s:" + subtitleIndex,
                "-c:s", "webvtt",
                out.toAbsolutePath().toString());
        runFfmpeg(cmd);
    }

    private void convertExternal(Path sidecar, Path out) throws IOException {
        String name = sidecar.getFileName().toString().toLowerCase();
        String text = SubtitleTextEncoding.readAsUtf8(sidecar);
        String vtt;
        if (name.endsWith(".vtt")) {
            vtt = SrtToWebVttConverter.ensureWebVttHeader(text);
        } else {
            vtt = SrtToWebVttConverter.convert(text);
        }
        byte[] bytes = vtt.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_VTT_BYTES) {
            throw new IOException("Subtitle output exceeds size cap");
        }
        CrewcastWriteGuard.check(out);
        Files.writeString(out, vtt, StandardCharsets.UTF_8);
    }

    private void runFfmpeg(List<String> cmd) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String logOut;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(line);
            }
            logOut = sb.toString();
        }
        if (!process.waitFor(CONVERT_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("ffmpeg subtitle conversion timed out");
        }
        if (process.exitValue() != 0) {
            throw new IOException("ffmpeg subtitle conversion failed: " + logOut);
        }
    }

    public record SubtitleDto(
            int index,
            String language,
            String languageName,
            String label,
            String source,
            boolean supported) {
    }
}
