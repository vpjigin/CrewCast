package com.solocrew.crewcast;

import com.solocrew.crewcast.media.AudioFixService;
import com.solocrew.crewcast.media.AudioFixStatus;
import com.solocrew.crewcast.media.BrowserPlayback;
import com.solocrew.crewcast.media.LanguageNames;
import com.solocrew.crewcast.media.ProbedMedia;
import com.solocrew.crewcast.media.SubtitleService;
import com.solocrew.crewcast.settings.DefaultCaptionsMode;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.share.VideoFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
@RestController
public class VideoController {

    private static final long INITIAL_CHUNK_SIZE = 1024 * 1024;

    private final ShareRegistry shareRegistry;
    private final SettingsStore settingsStore;
    private final AudioFixService audioFixService;
    private final SubtitleService subtitleService;

    public VideoController(
            ShareRegistry shareRegistry,
            SettingsStore settingsStore,
            AudioFixService audioFixService,
            SubtitleService subtitleService) {
        this.shareRegistry = shareRegistry;
        this.settingsStore = settingsStore;
        this.audioFixService = audioFixService;
        this.subtitleService = subtitleService;
    }

    @GetMapping("/api/videos")
    public List<VideoSummary> listVideos() throws IOException {
        return shareRegistry.listVideos().stream()
                .map(v -> new VideoSummary(v.id(), v.displayName()))
                .toList();
    }

    @GetMapping("/api/videos/{id}")
    public ResponseEntity<VideoDetails> videoDetails(
            @PathVariable String id,
            @RequestParam(name = "audio", required = false) Integer audio) throws Exception {

        VideoFile video = shareRegistry.findById(id).orElse(null);
        if (video == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = video.path();
        ProbedMedia media = audioFixService.probe(path);
        int selected = audio != null ? audio : audioFixService.defaultAudioIndex(media);
        AudioFixStatus audioStatus = audioFixService.statusFor(path, selected);
        String prepareHint = audioFixService.prepareTimeHint(path);

        List<AudioTrackDto> tracks = media.audioTracks().stream()
                .map(t -> {
                    boolean ready;
                    try {
                        ready = audioFixService.isTrackReady(path, t.audioIndex());
                    } catch (Exception e) {
                        ready = false;
                    }
                    String hint = ready ? null : prepareHint;
                    return new AudioTrackDto(
                            t.audioIndex(),
                            t.languageTag(),
                            LanguageNames.displayName(t.languageTag(), t.audioIndex()),
                            t.title(),
                            t.codecName(),
                            t.channels(),
                            BrowserPlayback.isBrowserAudioCodec(t.codecName()),
                            ready,
                            hint);
                })
                .toList();

        String warning = media.hevcInBrowserWarning()
                ? "This video uses H.265 (HEVC). Some browsers need the converted stream for reliable playback."
                : null;

        List<SubtitleService.SubtitleDto> subtitles = subtitleService.listForApi(path);
        DefaultCaptionsMode captionsDefault = settingsStore.getSettings().getDefaultCaptions();

        return ResponseEntity.ok(new VideoDetails(
                video.id(),
                video.displayName(),
                settingsStore.getSettings().isChapterThumbnails(),
                tracks,
                selected,
                new AudioProgressDto(audioStatus.status().name(), audioStatus.progress(), audioStatus.message()),
                media.mainVideoCodec(),
                warning,
                subtitles,
                captionsDefault.name()));
    }

    @GetMapping("/api/videos/{id}/audio-status")
    public ResponseEntity<AudioProgressDto> audioStatus(
            @PathVariable String id,
            @RequestParam(name = "audio", defaultValue = "0") int audio) throws Exception {

        Path path = shareRegistry.resolvePath(id).orElse(null);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }
        AudioFixStatus st = audioFixService.statusFor(path, audio, true);
        return ResponseEntity.ok(new AudioProgressDto(st.status().name(), st.progress(), st.message()));
    }

    @PostMapping("/api/videos/{id}/audio/prepare")
    public ResponseEntity<AudioProgressDto> prepareAudio(
            @PathVariable String id,
            @RequestParam(name = "audio", defaultValue = "0") int audio) throws Exception {

        Path path = shareRegistry.resolvePath(id).orElse(null);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }
        AudioFixStatus st = audioFixService.prepareAudio(path, audio);
        return ResponseEntity.ok(new AudioProgressDto(st.status().name(), st.progress(), st.message()));
    }

    @GetMapping("/stream/{id}")
    public ResponseEntity<?> stream(
            @PathVariable String id,
            @RequestParam(name = "audio", required = false) Integer audio,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) throws Exception {

        Path path = shareRegistry.resolvePath(id).orElse(null);
        if (path == null || !Files.exists(path) || !shareRegistry.isPathAllowed(path)) {
            return ResponseEntity.notFound().build();
        }

        ProbedMedia media = audioFixService.probe(path);
        if (media.audioTracks().isEmpty()) {
            return streamFile(path, rangeHeader);
        }

        int audioIndex = audio != null ? audio : audioFixService.defaultAudioIndex(media);
        if (audioIndex < 0 || audioIndex >= media.audioTracks().size()) {
            audioIndex = media.audioTracks().getFirst().audioIndex();
        }

        AudioFixService.ServeResult serve = audioFixService.resolvePlayback(path, audioIndex);
        if (serve.file().isEmpty()) {
            AudioFixStatus st = serve.status();
            String statusName = st.status().name();
            if (st.status() == com.solocrew.crewcast.media.AudioFixJobState.NOT_PREPARED) {
                statusName = "NOT_PREPARED";
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, "3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "status", statusName,
                            "progress", st.progress(),
                            "message", st.message() != null ? st.message() : ""));
        }

        Path servePath = serve.file().get();
        return streamFile(servePath, rangeHeader);
    }

    private ResponseEntity<Resource> streamFile(Path path, String rangeHeader) throws IOException {
        long contentLength = Files.size(path);

        long rangeStart;
        long rangeEnd;
        if (rangeHeader == null || rangeHeader.isBlank()) {
            rangeStart = 0;
            rangeEnd = contentLength > 0
                    ? Math.min(INITIAL_CHUNK_SIZE - 1, contentLength - 1)
                    : 0;
        } else {
            HttpRange parsed = HttpRange.parseRanges(rangeHeader).getFirst();
            rangeStart = parsed.getRangeStart(contentLength);
            rangeEnd = parsed.getRangeEnd(contentLength);
        }

        long regionCount = rangeEnd - rangeStart + 1;
        InputStream slice = new RangeFileInputStream(path, rangeStart, regionCount);
        InputStreamResource body = new InputStreamResource(slice) {
            @Override
            public long contentLength() {
                return regionCount;
            }
        };

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_LENGTH, Long.toString(regionCount))
                .header(HttpHeaders.CONTENT_RANGE,
                        "bytes " + rangeStart + "-" + rangeEnd + "/" + contentLength)
                .contentType(MediaType.parseMediaType(contentTypeFor(path)))
                .body(body);
    }

    /** Reads at most {@code length} bytes starting at {@code start} from a file. */
    private static final class RangeFileInputStream extends InputStream {
        private final InputStream in;
        private long remaining;

        RangeFileInputStream(Path path, long start, long length) throws IOException {
            in = Files.newInputStream(path);
            long toSkip = start;
            while (toSkip > 0) {
                long skipped = in.skip(toSkip);
                if (skipped <= 0) {
                    break;
                }
                toSkip -= skipped;
            }
            remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = in.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int read = in.read(b, off, toRead);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    private static String contentTypeFor(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp4") || name.endsWith(".m4v")) {
            return "video/mp4";
        }
        if (name.endsWith(".webm")) {
            return "video/webm";
        }
        if (name.endsWith(".mkv")) {
            return "video/x-matroska";
        }
        if (name.endsWith(".mov")) {
            return "video/quicktime";
        }
        return "video/mp4";
    }

    public record VideoSummary(String id, String title) {
    }

    public record AudioTrackDto(
            int index,
            String lang,
            String languageName,
            String title,
            String codec,
            int channels,
            boolean browserPlayable,
            boolean ready,
            String prepareTimeHint) {
    }

    public record AudioProgressDto(String status, int progress, String message) {
    }

    public record VideoDetails(
            String id,
            String title,
            boolean chaptersEnabled,
            List<AudioTrackDto> audioTracks,
            int selectedAudio,
            AudioProgressDto audio,
            String videoCodec,
            String warning,
            List<SubtitleService.SubtitleDto> subtitles,
            String defaultCaptions) {
    }
}
