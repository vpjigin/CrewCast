package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.PrepareAudioMode;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.settings.ShareEntry;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.system.CrewcastPaths;
import com.solocrew.crewcast.system.CrewcastWriteGuard;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AudioFixService {

    private static final Logger log = LoggerFactory.getLogger(AudioFixService.class);
    private static final long MIN_FREE_BYTES = 512L * 1024 * 1024;
    private final FfmpegManager ffmpegManager;
    private final SettingsStore settingsStore;
    private final ShareRegistry shareRegistry;

    private final Map<String, MutableJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "audio-fix-worker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean workerStarted = new AtomicBoolean(false);

    private volatile Process runningProcess;
    private volatile String runningCacheKey;
    private volatile double remuxBytesPerSecond;

    public AudioFixService(FfmpegManager ffmpegManager, SettingsStore settingsStore, ShareRegistry shareRegistry) {
        this.ffmpegManager = ffmpegManager;
        this.settingsStore = settingsStore;
        this.shareRegistry = shareRegistry;
    }

    @jakarta.annotation.PostConstruct
    void startup() throws IOException {
        CrewcastWriteGuard.check(CrewcastPaths.cacheDir());
        Files.createDirectories(CrewcastPaths.cacheDir());
        ConversionPartFiles.deleteStaleAudioParts(CrewcastPaths.cacheDir());
        startWorkerIfNeeded();
    }

    @PreDestroy
    void shutdown() {
        cancelAllJobs(true);
        worker.shutdownNow();
    }

    public boolean isFeatureEnabled() {
        return ffmpegManager.isAvailable() && settingsStore.getSettings().isFixAudioForBrowsers();
    }

    public void recheckFfmpeg() {
        ffmpegManager.recheck();
    }

    public String ffmpegInstallHint() {
        return FfmpegManager.installHint();
    }

    public void onSharingStarted() {
        if (!isFeatureEnabled()) {
            return;
        }
        PrepareAudioMode mode = settingsStore.getSettings().getPrepareAudioInAdvance();
        if (mode == PrepareAudioMode.OFF) {
            return;
        }
        try {
            for (Path video : shareRegistry.listVideoPaths()) {
                try {
                    ProbedMedia media = probe(video);
                    int defaultAudio = defaultAudioIndex(media);
                    List<Integer> order = new ArrayList<>();
                    if (mode == PrepareAudioMode.ALL_TRACKS) {
                        order.add(defaultAudio);
                        for (int i = 0; i < media.audioTracks().size(); i++) {
                            if (i != defaultAudio) {
                                order.add(i);
                            }
                        }
                    } else {
                        order.add(defaultAudio);
                    }
                    for (int audio : order) {
                        if (needsTranscode(video, media, audio)) {
                            enqueueInternal(video, media, audio, false, true, false, null, null);
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Pre-convert skip {}: {}", video, ex.getMessage());
                }
            }
        } catch (IOException ex) {
            log.debug("Pre-convert list failed: {}", ex.getMessage());
        }
    }

    public boolean hevcWarningForShare(ShareEntry entry) {
        if (!ffmpegManager.isAvailable()) {
            return false;
        }
        try {
            Path path = Path.of(entry.getPath());
            if (entry.getKind() == ShareEntry.Kind.FILE) {
                return probe(path).hevcInBrowserWarning();
            }
            for (Path v : shareRegistry.listVideosUnder(path)) {
                if (probe(v).hevcInBrowserWarning()) {
                    return true;
                }
            }
        } catch (Exception ex) {
            log.debug("HEVC check skip: {}", ex.getMessage());
        }
        return false;
    }

    public void onSharingStopped() {
        cancelAllJobs(true);
    }

    public void onShareRemoved(Path shareRoot) {
        cancelJobsForPathPrefix(shareRoot);
    }

    public void clearCache() throws IOException {
        cancelAllJobs(true);
        Path cache = CrewcastPaths.cacheDir();
        if (!Files.isDirectory(cache)) {
            return;
        }
        deleteCacheTree(cache);
        jobs.clear();
    }

    private static void deleteCacheTree(Path cache) throws IOException {
        if (!Files.isDirectory(cache)) {
            return;
        }
        try (var walk = Files.walk(cache)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
        Files.createDirectories(cache);
    }

    public long cacheSizeBytes() throws IOException {
        Path cache = CrewcastPaths.cacheDir();
        if (!Files.isDirectory(cache)) {
            return 0;
        }
        long total = 0;
        try (var stream = Files.list(cache)) {
            for (Path p : stream.toList()) {
                if (Files.isRegularFile(p)) {
                    total += Files.size(p);
                }
            }
        }
        return total;
    }

    public boolean isTrackReady(Path source, int audioIndex) throws IOException, InterruptedException {
        if (!isFeatureEnabled()) {
            return true;
        }
        ProbedMedia media = probe(source);
        if (media.audioTracks().isEmpty()) {
            return true;
        }
        if (BrowserPlayback.canServeOriginal(source, media, audioIndex)) {
            return true;
        }
        return Files.isRegularFile(cacheFile(source, audioIndex));
    }

    public AudioFixStatus statusFor(Path source, int audioIndex) throws IOException, InterruptedException {
        return statusFor(source, audioIndex, false);
    }

    public AudioFixStatus statusFor(Path source, int audioIndex, boolean unusedRecordPoll)
            throws IOException, InterruptedException {
        String key = jobKey(source, audioIndex);
        MutableJob job = jobs.get(key);
        if (job != null && job.state != AudioFixJobState.FAILED) {
            if (job.state == AudioFixJobState.READY || Files.isRegularFile(cacheFile(source, audioIndex))) {
                return readyStatus(key);
            }
            return job.toStatus(key, queueAheadFor(source, audioIndex));
        }
        Path cached = cacheFile(source, audioIndex);
        if (Files.isRegularFile(cached)) {
            touch(cached);
            return readyStatus(key);
        }
        if (!isFeatureEnabled()) {
            return readyStatus(key);
        }
        ProbedMedia media = probe(source);
        if (media.audioTracks().isEmpty()) {
            return readyStatus(key);
        }
        if (BrowserPlayback.canServeOriginal(source, media, audioIndex)) {
            return readyStatus(key);
        }
        return new AudioFixStatus(
                AudioFixJobState.NOT_PREPARED, 0, "Not prepared", key,
                null, null, null, null, null, null);
    }

    public AudioFixStatus prepareAudio(Path source, int audioIndex) throws IOException, InterruptedException {
        return prepareAudio(source, audioIndex, null);
    }

    public AudioFixStatus prepareAudio(Path source, int audioIndex, String viewerLabel)
            throws IOException, InterruptedException {
        return prepareAudio(source, audioIndex, viewerLabel, null);
    }

    public AudioFixStatus prepareAudio(Path source, int audioIndex, String viewerLabel, String clientId)
            throws IOException, InterruptedException {
        if (!isFeatureEnabled()) {
            return statusFor(source, audioIndex);
        }
        ProbedMedia media = probe(source);
        String key = jobKey(source, audioIndex);
        if (media.audioTracks().isEmpty()) {
            return readyStatus(key);
        }
        if (BrowserPlayback.canServeOriginal(source, media, audioIndex)) {
            return readyStatus(key);
        }
        if (Files.isRegularFile(cacheFile(source, audioIndex))) {
            return readyStatus(key);
        }
        MutableJob existing = jobs.get(key);
        if (existing == null || existing.state == AudioFixJobState.FAILED) {
            if (countViewerQueuedJobs() >= ConversionLimits.MAX_VIEWER_QUEUED_JOBS) {
                return new AudioFixStatus(
                        AudioFixJobState.FAILED, 0, ConversionLimits.HOST_BUSY_MESSAGE, key,
                        null, null, null, null, null, null);
            }
        }
        enqueueInternal(source, media, audioIndex, true, false, true, viewerLabel, clientId);
        return statusFor(source, audioIndex);
    }

    private AudioFixStatus readyStatus(String key) {
        return new AudioFixStatus(
                AudioFixJobState.READY, 100, "Ready", key, null, null, null, null, null, null);
    }

    public String prepareTimeHint(Path source) {
        try {
            double durationSec = probeDuration(source);
            double bytesPerSec = remuxBytesPerSecond;
            if (bytesPerSec > 0 && durationSec > 0) {
                long estSource = Files.size(source);
                long estOut = (long) (estSource * 0.85);
                long seconds = (long) Math.ceil(estOut / bytesPerSec);
                if (seconds < 90) {
                    return "about " + Math.max(1, seconds / 60) + " min";
                }
                return "about " + (seconds / 60) + " min";
            }
        } catch (Exception ignored) {
        }
        return "a few minutes";
    }

    public String summarizeForShare(ShareEntry entry) {
        if (!isFeatureEnabled()) {
            return "";
        }
        try {
            if (entry.getKind() == ShareEntry.Kind.FILE) {
                return summarizeForVideo(Path.of(entry.getPath()));
            }
            String best = null;
            for (Path v : shareRegistry.listVideosUnder(Path.of(entry.getPath()))) {
                String line = summarizeForVideo(v);
                if (best == null || line.contains("converting")) {
                    best = line;
                }
            }
            return best == null ? "Audio: not prepared" : best;
        } catch (Exception ex) {
            return "Audio: unavailable";
        }
    }

    private String summarizeForVideo(Path videoPath) throws IOException, InterruptedException {
        Path norm = videoPath.toAbsolutePath().normalize();
        MutableJob active = null;
        for (MutableJob job : jobs.values()) {
            if (!job.source.equals(norm)) {
                continue;
            }
            if (job.state == AudioFixJobState.RUNNING || job.state == AudioFixJobState.QUEUED) {
                active = job;
                break;
            }
        }
        if (active != null) {
            String lang = trackLanguageName(active.media, active.audioIndex);
            if (active.state == AudioFixJobState.RUNNING) {
                return "Audio: converting " + active.progress + "% (" + lang + ")";
            }
            return "Audio: queued (" + lang + ")";
        }
        ProbedMedia media = probe(videoPath);
        List<String> readyLangs = new ArrayList<>();
        for (var tr : media.audioTracks()) {
            int idx = tr.audioIndex();
            if (isTrackReady(videoPath, idx)) {
                if (!BrowserPlayback.canServeOriginal(videoPath, media, idx)
                        && Files.isRegularFile(cacheFile(videoPath, idx))) {
                    readyLangs.add(LanguageNames.displayName(tr.languageTag(), idx));
                }
            }
        }
        if (!readyLangs.isEmpty()) {
            return "Audio: ready (" + readyLangs.getFirst() + (readyLangs.size() > 1 ? ", …" : "") + ")";
        }
        return "Audio: not prepared";
    }

    private static String trackLanguageName(ProbedMedia media, int audioIndex) {
        return media.audioTracks().stream()
                .filter(t -> t.audioIndex() == audioIndex)
                .findFirst()
                .map(t -> LanguageNames.displayName(t.languageTag(), t.audioIndex()))
                .orElse("track " + audioIndex);
    }

    public record ServeResult(Optional<Path> file, boolean original, AudioFixStatus status) {
    }

    public ServeResult resolvePlayback(Path source, int audioIndex) throws IOException, InterruptedException {
        AppSettings settings = settingsStore.getSettings();
        ProbedMedia media = probe(source);
        if (media.audioTracks().isEmpty()) {
            return new ServeResult(Optional.of(source), true, statusFor(source, audioIndex));
        }
        if (!settings.isFixAudioForBrowsers() || !ffmpegManager.isAvailable()) {
            return new ServeResult(Optional.of(source), true, statusFor(source, audioIndex));
        }
        if (BrowserPlayback.canServeOriginal(source, media, audioIndex)) {
            return new ServeResult(Optional.of(source), true, statusFor(source, audioIndex));
        }
        Path cached = cacheFile(source, audioIndex);
        if (Files.isRegularFile(cached)) {
            touch(cached);
            return new ServeResult(Optional.of(cached), false,
                    new AudioFixStatus(
                            AudioFixJobState.READY, 100, "Ready", jobKey(source, audioIndex),
                            null, null, null, null, null, null));
        }
        if (audioIndex < 0 || media.audioByRelativeIndex(audioIndex).isEmpty()) {
            return new ServeResult(Optional.of(source), true, statusFor(source, audioIndex));
        }
        return new ServeResult(Optional.empty(), false, statusFor(source, audioIndex));
    }

    public int defaultAudioIndex(ProbedMedia media) {
        AppSettings s = settingsStore.getSettings();
        String pref = s.getPreferredAudioLanguage();
        if ("auto".equalsIgnoreCase(pref)) {
            return 0;
        }
        return AudioTrackSelector.selectAudioIndex(media, null, pref);
    }

    public boolean needsTranscode(Path source, ProbedMedia media, int audioIndex) {
        if (!settingsStore.getSettings().isFixAudioForBrowsers() || !ffmpegManager.isAvailable()) {
            return false;
        }
        return !BrowserPlayback.canServeOriginal(source, media, audioIndex);
    }

    public ProbedMedia probe(Path file) throws IOException, InterruptedException {
        if (!ffmpegManager.isAvailable()) {
            throw new IOException("ffprobe not available");
        }
        return MediaProbe.probe(file, ffmpegManager.ffprobe());
    }

    /** Test hook: same as prepare path enqueue. */
    public void enqueue(Path source, ProbedMedia media, int audioIndex, boolean highPriority) {
        enqueueInternal(source, media, audioIndex, highPriority, false, true, null, null);
    }

    /** Viewer-initiated jobs waiting to start; {@code RUNNING} jobs are not counted toward the cap. */
    private int countViewerQueuedJobs() {
        return (int) jobs.values().stream()
                .filter(j -> j.state == AudioFixJobState.QUEUED
                        && j.startedBy == ConversionStartedBy.VIEWER
                        && !j.hostInitiated)
                .count();
    }

    public int queueAheadFor(Path source, int audioIndex) throws IOException {
        String key = jobKey(source, audioIndex);
        MutableJob job = jobs.get(key);
        if (job == null || job.state != AudioFixJobState.QUEUED) {
            return 0;
        }
        long mine = job.enqueuedAt;
        return (int) jobs.values().stream()
                .filter(j -> j.state == AudioFixJobState.QUEUED && j.enqueuedAt < mine)
                .count();
    }

    private void enqueueInternal(
            Path source,
            ProbedMedia media,
            int audioIndex,
            boolean highPriority,
            boolean hostInitiated,
            boolean viewerInitiated,
            String viewerLabel,
            String clientId) {
        if (!ffmpegManager.isAvailable()) {
            return;
        }
        try {
            String key = jobKey(source, audioIndex);
            jobs.computeIfAbsent(key, k -> new MutableJob(source, audioIndex, media));
            MutableJob job = jobs.get(key);
            if (Files.isRegularFile(cacheFile(source, audioIndex))) {
                job.state = AudioFixJobState.READY;
                job.progress = 100;
                return;
            }
            if (job.state == AudioFixJobState.READY) {
                return;
            }
            if (job.startedBy == null) {
                job.startedBy = viewerInitiated ? ConversionStartedBy.VIEWER : ConversionStartedBy.HOST;
                job.startedByLabel = viewerInitiated ? viewerLabel : null;
                job.startedByClientId = viewerInitiated ? ConversionClientIds.normalize(clientId) : null;
            } else if (viewerInitiated && viewerLabel != null && job.startedBy == ConversionStartedBy.VIEWER) {
                if (job.startedByLabel == null || job.startedByLabel.isBlank()) {
                    job.startedByLabel = viewerLabel;
                }
            }
            job.hostInitiated = job.hostInitiated || hostInitiated;
            job.viewerInitiated = job.viewerInitiated || viewerInitiated;
            if (job.state == AudioFixJobState.RUNNING) {
                if (highPriority) {
                    job.highPriority = true;
                }
                return;
            }
            if (highPriority) {
                job.highPriority = true;
                preemptLowPriorityRunningJob();
            }
            job.state = AudioFixJobState.QUEUED;
            job.message = "Queued";
            job.enqueuedAt = System.nanoTime();
            startWorkerIfNeeded();
            worker.submit(this::drainQueue);
        } catch (IOException ex) {
            log.warn("Enqueue failed: {}", ex.getMessage());
        }
    }

    private void cancelJob(MutableJob job) {
        String key = null;
        try {
            key = jobKey(job.source, job.audioIndex);
        } catch (IOException ignored) {
        }
        boolean running = key != null && key.equals(runningCacheKey);
        Process p = running ? runningProcess : null;
        if (p != null) {
            p.destroyForcibly();
        }
        jobs.entrySet().removeIf(e -> e.getValue() == job);
        if (p != null) {
            try {
                p.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            Path out = cacheFile(job.source, job.audioIndex);
            ConversionPartFiles.deleteIfExists(out);
        } catch (IOException ignored) {
        }
    }

    private void preemptLowPriorityRunningJob() {
        String key = runningCacheKey;
        if (key == null) {
            return;
        }
        MutableJob running = jobs.get(key);
        if (running == null || running.highPriority) {
            return;
        }
        Process p = runningProcess;
        if (p != null) {
            running.state = AudioFixJobState.QUEUED;
            running.message = "Queued";
            running.progress = 0;
            p.destroyForcibly();
        }
    }

    private final AtomicBoolean draining = new AtomicBoolean();

    private void drainQueue() {
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        try {
            while (true) {
                Optional<Map.Entry<String, MutableJob>> next = jobs.entrySet().stream()
                        .filter(e -> e.getValue().state == AudioFixJobState.QUEUED)
                        .sorted(Comparator
                                .comparing((Map.Entry<String, MutableJob> e) -> !e.getValue().highPriority)
                                .thenComparingLong(e -> e.getValue().enqueuedAt))
                        .findFirst();
                if (next.isEmpty()) {
                    break;
                }
                runJob(next.get().getKey(), next.get().getValue());
            }
        } finally {
            draining.set(false);
        }
    }

    private void runJob(String cacheKey, MutableJob job) {
        Path out;
        try {
            out = cacheFile(job.source, job.audioIndex);
        } catch (IOException e) {
            job.fail(e.getMessage());
            return;
        }
        Path part = ConversionPartFiles.partPath(out);
        job.outputPath = out;
        long started = System.currentTimeMillis();
        try {
            if (!ensureDiskSpace()) {
                job.fail("Not enough free disk space for audio cache (need at least 512 MB free)");
                return;
            }
            evictIfNeeded();
            job.state = AudioFixJobState.RUNNING;
            job.progress = 0;
            job.message = "Starting ffmpeg";
            job.speed = null;
            job.etaSeconds = null;
            double durationSec = probeDuration(job.source);
            job.durationSec = durationSec;
            List<String> cmd = FfmpegTranscodePlanner.buildTranscodeCommand(
                    ffmpegManager.ffmpeg(), job.source, job.media, job.audioIndex, part);
            log.info("Crewcast ffmpeg: {}", String.join(" ", cmd));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            runningProcess = process;
            runningCacheKey = cacheKey;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    FfmpegProgressParser.Snapshot snap =
                            FfmpegProgressParser.applyLine(line, job.durationSec, job.progress);
                    job.progress = snap.progress;
                    if (snap.speed != null && snap.speed > 0) {
                        job.speed = snap.speed;
                    }
                    if (snap.etaSeconds != null) {
                        job.etaSeconds = snap.etaSeconds;
                    }
                    if (line.startsWith("out_time_us=") || line.startsWith("progress=end")) {
                        job.message = "Converting";
                    }
                }
            }
            int code = process.waitFor();
            runningProcess = null;
            runningCacheKey = null;
            if (job.state != AudioFixJobState.RUNNING) {
                Files.deleteIfExists(part);
                return;
            }
            if (code != 0 || !Files.isRegularFile(part) || Files.size(part) < 1024) {
                Files.deleteIfExists(part);
                job.fail("ffmpeg exited with code " + code);
                return;
            }
            CrewcastWriteGuard.check(out);
            Files.move(part, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            touch(out);
            job.state = AudioFixJobState.READY;
            job.progress = 100;
            job.message = "Ready";
            long elapsedMs = Math.max(1, System.currentTimeMillis() - started);
            long outBytes = Files.size(out);
            double bps = outBytes / (elapsedMs / 1000.0);
            if (remuxBytesPerSecond <= 0) {
                remuxBytesPerSecond = bps;
            } else {
                remuxBytesPerSecond = remuxBytesPerSecond * 0.7 + bps * 0.3;
            }
            log.info("Crewcast audio cache ready: {}", out);
        } catch (Exception ex) {
            job.fail(ex.getMessage());
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
            }
        } finally {
            runningProcess = null;
            runningCacheKey = null;
            worker.submit(this::drainQueue);
        }
    }

    private double probeDuration(Path file) {
        try {
            Process p = new ProcessBuilder(
                    ffmpegManager.ffprobe().toString(),
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    file.toString())
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                p.waitFor(10, TimeUnit.SECONDS);
                return line != null ? Double.parseDouble(line.trim()) : 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean ensureDiskSpace() throws IOException {
        FileStore store = Files.getFileStore(CrewcastPaths.cacheDir());
        return store.getUsableSpace() >= MIN_FREE_BYTES;
    }

    private void evictIfNeeded() throws IOException {
        long cap = (long) settingsStore.getSettings().getAudioCacheMaxGb() * 1024L * 1024L * 1024L;
        long size = cacheSizeBytes();
        if (size <= cap) {
            return;
        }
        java.util.Set<Path> protectedOutputs = jobs.values().stream()
                .filter(j -> j.state == AudioFixJobState.RUNNING || j.state == AudioFixJobState.QUEUED)
                .map(j -> {
                    try {
                        return cacheFile(j.source, j.audioIndex).toAbsolutePath().normalize();
                    } catch (IOException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(CrewcastPaths.cacheDir())) {
            for (Path p : stream.toList()) {
                if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".mp4")) {
                    if (!protectedOutputs.contains(p.toAbsolutePath().normalize())) {
                        files.add(p);
                    }
                }
            }
        }
        files.sort(Comparator.comparingLong(p -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return 0;
            }
        }));
        for (Path p : files) {
            if (cacheSizeBytes() <= cap) {
                break;
            }
            Files.deleteIfExists(p);
            jobs.values().removeIf(j -> j.state == AudioFixJobState.READY);
        }
    }

    private void touch(Path file) throws IOException {
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
    }

    private Path cacheFile(Path source, int audioIndex) throws IOException {
        return CrewcastPaths.cacheDir().resolve(AudioCacheKeys.cacheFileName(source, audioIndex));
    }

    private String jobKey(Path source, int audioIndex) throws IOException {
        return AudioCacheKeys.cacheFileName(source, audioIndex).replace(".mp4", "");
    }

    public void cleanupStaleParts() throws IOException {
        ConversionPartFiles.deleteStaleAudioParts(CrewcastPaths.cacheDir());
    }

    public void cancelAllJobs(boolean wait) {
        Process p = runningProcess;
        if (p != null) {
            p.destroyForcibly();
        }
        for (MutableJob job : new ArrayList<>(jobs.values())) {
            if (job.outputPath != null) {
                ConversionPartFiles.deleteIfExists(job.outputPath);
            }
        }
        if (wait && p != null) {
            try {
                p.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        jobs.clear();
    }

    private void cancelJobsForPathPrefix(Path shareRoot) {
        Path norm = shareRoot.toAbsolutePath().normalize();
        List<MutableJob> toCancel = jobs.values().stream()
                .filter(j -> j.source.startsWith(norm))
                .toList();
        for (MutableJob job : toCancel) {
            cancelJob(job);
        }
    }

    private void startWorkerIfNeeded() {
        workerStarted.set(true);
    }

    private static final class MutableJob {
        final Path source;
        final int audioIndex;
        final ProbedMedia media;
        volatile AudioFixJobState state = AudioFixJobState.QUEUED;
        volatile int progress;
        volatile String message = "Queued";
        volatile boolean highPriority;
        volatile boolean viewerInitiated;
        volatile boolean hostInitiated;
        volatile long enqueuedAt = System.nanoTime();
        volatile ConversionStartedBy startedBy;
        volatile String startedByLabel;
        volatile String startedByClientId;
        volatile Double speed;
        volatile Integer etaSeconds;
        volatile double durationSec;
        volatile Path outputPath;

        MutableJob(Path source, int audioIndex, ProbedMedia media) {
            this.source = source.toAbsolutePath().normalize();
            this.audioIndex = audioIndex;
            this.media = media;
        }

        AudioFixStatus toStatus(String key, int queueAhead) {
            return new AudioFixStatus(
                    state,
                    progress,
                    message,
                    key,
                    speed,
                    etaSeconds,
                    startedBy,
                    startedByLabel,
                    queueAhead,
                    startedByClientId);
        }

        void fail(String msg) {
            state = AudioFixJobState.FAILED;
            message = msg != null ? msg : "Failed";
        }
    }
}
