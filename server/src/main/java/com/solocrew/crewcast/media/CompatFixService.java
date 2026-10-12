package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.PrepareCompatMode;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CompatFixService {

    public static final String NO_ENCODER_MESSAGE =
            "This computer cannot convert video for this device. Try another device or play the file with a native player like VLC.";

    private static final Logger log = LoggerFactory.getLogger(CompatFixService.class);
    private static final long MIN_FREE_BYTES = 512L * 1024 * 1024;
    private static final long IDLE_CANCEL_MS = 120_000L;

    private final FfmpegManager ffmpegManager;
    private final AudioFixService audioFixService;
    private final SettingsStore settingsStore;
    private final ShareRegistry shareRegistry;

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Runnable> progressListeners = new CopyOnWriteArrayList<>();
    private volatile long lastProgressNotifyMs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "compat-fix-worker");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService idleWatchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "compat-idle-watch");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean draining = new AtomicBoolean();
    private volatile Process runningProcess;
    private volatile String runningKey;
    private volatile String resolvedEncoder;

    private final ScheduledExecutorService autoPrepareScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "compat-auto-prepare");
        t.setDaemon(true);
        return t;
    });
    private volatile java.util.concurrent.ScheduledFuture<?> debouncedScan;
    private volatile java.util.concurrent.ScheduledFuture<?> periodicRescan;

    public CompatFixService(
            FfmpegManager ffmpegManager,
            AudioFixService audioFixService,
            SettingsStore settingsStore,
            ShareRegistry shareRegistry) {
        this.ffmpegManager = ffmpegManager;
        this.audioFixService = audioFixService;
        this.settingsStore = settingsStore;
        this.shareRegistry = shareRegistry;
    }

    public void addProgressListener(Runnable listener) {
        if (listener != null) {
            progressListeners.add(listener);
        }
    }

    public void removeProgressListener(Runnable listener) {
        progressListeners.remove(listener);
    }

    @jakarta.annotation.PostConstruct
    void startup() {
        idleWatchdog.scheduleWithFixedDelay(this::cancelIdleJobs, 15, 15, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        idleWatchdog.shutdownNow();
        autoPrepareScheduler.shutdownNow();
        stopPeriodicRescan();
        cancelAll(true);
        worker.shutdownNow();
    }

    public boolean isAvailable() {
        if (!ffmpegManager.isAvailable()) {
            return false;
        }
        try {
            return resolveEncoderName() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static final String VIEWER_PREPARE_BLOCKED_MESSAGE =
            "The host has not prepared a compatible version of this video.";

    public void onSharingStopped() {
        stopPeriodicRescan();
        cancelAll(true);
    }

    public void onSharingStarted() {
        scheduleAutoPrepareScan();
        startPeriodicRescan();
    }

    public void onShareRemoved(Path shareRoot) {
        cancelJobsForPathPrefix(shareRoot);
    }

    public void scheduleAutoPrepareScan() {
        if (debouncedScan != null) {
            debouncedScan.cancel(false);
        }
        debouncedScan = autoPrepareScheduler.schedule(this::runAutoPrepareScan, 2, TimeUnit.SECONDS);
    }

    public AudioFixStatus prepareCompat(Path source, int audioIndex) throws IOException, InterruptedException {
        return prepareCompat(source, audioIndex, true);
    }

    public AudioFixStatus prepareCompat(Path source, int audioIndex, boolean viewerInitiated)
            throws IOException, InterruptedException {
        if (viewerInitiated && !settingsStore.getSettings().isAllowViewerCompatPrepare()) {
            return failedStatus(source, audioIndex, VIEWER_PREPARE_BLOCKED_MESSAGE);
        }
        if (!ffmpegManager.isAvailable()) {
            return failedStatus(source, audioIndex, "ffmpeg not available");
        }
        if (resolveEncoderName() == null) {
            return failedStatus(source, audioIndex, NO_ENCODER_MESSAGE);
        }
        ProbedMedia media = audioFixService.probe(source);
        if (Files.isRegularFile(cacheFile(source, audioIndex))) {
            return readyStatus(source, audioIndex);
        }
        enqueue(source, media, audioIndex, viewerInitiated, !viewerInitiated);
        if (viewerInitiated) {
            touchPoll(source, audioIndex);
        }
        notifyProgressThrottled();
        return statusFor(source, audioIndex, false);
    }

    public AudioFixStatus prepareHostDefaultTrack(Path source) throws IOException, InterruptedException {
        ProbedMedia media = audioFixService.probe(source);
        int audio = audioFixService.defaultAudioIndex(media);
        return prepareCompat(source, audio, false);
    }

    public void cancelForVideo(Path source) throws IOException, InterruptedException {
        String key = jobKey(source, audioFixService.defaultAudioIndex(audioFixService.probe(source)));
        Job job = jobs.get(key);
        if (job != null && (job.state == AudioFixJobState.QUEUED || job.state == AudioFixJobState.RUNNING)) {
            cancelJob(job);
            notifyProgressThrottled();
        }
    }

    public CompatUiState uiStateForDefaultTrack(Path source) throws IOException, InterruptedException {
        ProbedMedia media = audioFixService.probe(source);
        int audio = audioFixService.defaultAudioIndex(media);
        PrepareCompatMode mode = settingsStore.getSettings().getPrepareCompatInAdvance();
        boolean cacheReady = Files.isRegularFile(cacheFile(source, audio));
        AudioFixStatus st = statusFor(source, audio, false);
        return CompatStatusMapper.map(mode, media, isAvailable(), st, cacheReady);
    }

    public String tooltipForDefaultTrack(Path source) throws IOException, InterruptedException {
        CompatUiState state = uiStateForDefaultTrack(source);
        ProbedMedia media = audioFixService.probe(source);
        int audio = audioFixService.defaultAudioIndex(media);
        AudioFixStatus st = statusFor(source, audio, false);
        return describeUiState(state, st.progress(), st.message());
    }

    public int progressForDefaultTrack(Path source) throws IOException, InterruptedException {
        ProbedMedia media = audioFixService.probe(source);
        int audio = audioFixService.defaultAudioIndex(media);
        return statusFor(source, audio, false).progress();
    }

    public static String describeUiState(CompatUiState state, int progress, String detail) {
        return switch (state) {
            case NOT_NEEDED -> "TV version: not needed (already browser-safe)";
            case NEEDED -> "TV version: not prepared yet";
            case QUEUED -> "TV version: waiting in queue";
            case CONVERTING -> "TV version: converting… " + progress + "%";
            case READY -> "TV version: ready";
            case UNAVAILABLE -> detail != null && !detail.isBlank() ? detail : NO_ENCODER_MESSAGE;
            case FAILED -> detail != null && !detail.isBlank() ? detail : "TV version: conversion failed";
        };
    }

    public String summarizeForShare(ShareEntry entry) {
        try {
            if (entry.getKind() == ShareEntry.Kind.FILE) {
                return summarizeForVideo(Path.of(entry.getPath()));
            }
            int ready = 0;
            int converting = 0;
            int needed = 0;
            int failed = 0;
            int notNeeded = 0;
            int queued = 0;
            int convertingPct = 0;
            for (Path v : shareRegistry.listVideosUnder(Path.of(entry.getPath()))) {
                CompatUiState s = uiStateForDefaultTrack(v);
                switch (s) {
                    case READY -> ready++;
                    case CONVERTING -> {
                        converting++;
                        AudioFixStatus st = statusFor(v, audioFixService.defaultAudioIndex(audioFixService.probe(v)), false);
                        convertingPct = st.progress();
                    }
                    case NEEDED -> needed++;
                    case QUEUED -> queued++;
                    case FAILED -> failed++;
                    case NOT_NEEDED, UNAVAILABLE -> notNeeded++;
                }
            }
            if (ready == 0 && converting == 0 && needed == 0 && queued == 0 && failed == 0) {
                return notNeeded > 0 ? "TV version: not needed" : "";
            }
            StringBuilder sb = new StringBuilder("TV version:");
            if (ready > 0) {
                sb.append(" ").append(ready).append(" ready");
            }
            if (converting > 0) {
                sb.append(ready > 0 ? "," : "").append(" ").append(converting)
                        .append(" converting ").append(convertingPct).append("%");
            }
            if (queued > 0) {
                sb.append(" ").append(queued).append(" queued");
            }
            if (needed > 0) {
                sb.append(" ").append(needed).append(" needed");
            }
            if (failed > 0) {
                sb.append(" ").append(failed).append(" failed");
            }
            return sb.toString().trim();
        } catch (Exception ex) {
            return "TV version: unavailable";
        }
    }

    private String summarizeForVideo(Path video) throws IOException, InterruptedException {
        CompatUiState s = uiStateForDefaultTrack(video);
        AudioFixStatus st = statusFor(video, audioFixService.defaultAudioIndex(audioFixService.probe(video)), false);
        return describeUiState(s, st.progress(), st.message());
    }

    public void prepareAllNeedingUnder(Path folderRoot) throws IOException, InterruptedException {
        for (Path v : shareRegistry.listVideosUnder(folderRoot)) {
            ProbedMedia media = audioFixService.probe(v);
            if (CompatPlaybackPolicy.videoNeedsCompatTranscode(media)) {
                prepareHostDefaultTrack(v);
            }
        }
    }

    public void prepareAllVideosUnder(Path folderRoot) throws IOException, InterruptedException {
        for (Path v : shareRegistry.listVideosUnder(folderRoot)) {
            prepareHostDefaultTrack(v);
        }
    }

    void runAutoPrepareScan() {
        AppSettings settings = settingsStore.getSettings();
        PrepareCompatMode mode = settings.getPrepareCompatInAdvance();
        if (mode == PrepareCompatMode.OFF || !ffmpegManager.isAvailable()) {
            return;
        }
        try {
            if (resolveEncoderName() == null) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (IOException e) {
            log.debug("Compat auto-prepare encoder check failed: {}", e.getMessage());
            return;
        }
        try {
            for (Path video : shareRegistry.listVideoPaths()) {
                try {
                    autoPrepareOne(video, mode);
                } catch (Exception ex) {
                    log.debug("Compat auto-prepare skip {}: {}", video, ex.getMessage());
                }
            }
            notifyProgressThrottled();
        } catch (IOException ex) {
            log.debug("Compat auto-prepare list failed: {}", ex.getMessage());
        }
    }

    private void autoPrepareOne(Path video, PrepareCompatMode mode) throws IOException, InterruptedException {
        if (!CompatPreparePolicy.shouldAutoPrepare(mode, audioFixService.probe(video))) {
            return;
        }
        int audio = audioFixService.defaultAudioIndex(audioFixService.probe(video));
        if (Files.isRegularFile(cacheFile(video, audio))) {
            return;
        }
        String key = jobKey(video, audio);
        Job existing = jobs.get(key);
        if (existing != null && existing.state != AudioFixJobState.FAILED) {
            return;
        }
        enqueue(video, audioFixService.probe(video), audio, false, true);
    }

    private void startPeriodicRescan() {
        stopPeriodicRescan();
        periodicRescan = autoPrepareScheduler.scheduleAtFixedRate(
                this::runAutoPrepareScan, 45, 45, TimeUnit.SECONDS);
    }

    private void stopPeriodicRescan() {
        if (periodicRescan != null) {
            periodicRescan.cancel(false);
            periodicRescan = null;
        }
    }

    private void cancelJobsForPathPrefix(Path prefix) {
        Path norm = prefix.toAbsolutePath().normalize();
        List<Job> toCancel = jobs.values().stream()
                .filter(j -> j.source.startsWith(norm))
                .toList();
        for (Job job : toCancel) {
            cancelJob(job);
        }
        notifyProgressThrottled();
    }

    public AudioFixStatus statusFor(Path source, int audioIndex, boolean recordPoll) throws IOException {
        if (recordPoll) {
            touchPoll(source, audioIndex);
        }
        String key = jobKey(source, audioIndex);
        Job job = jobs.get(key);
        if (job != null && job.state != AudioFixJobState.FAILED) {
            if (job.state == AudioFixJobState.READY || Files.isRegularFile(cacheFile(source, audioIndex))) {
                return readyStatus(source, audioIndex);
            }
            return job.toStatus(key);
        }
        if (Files.isRegularFile(cacheFile(source, audioIndex))) {
            return readyStatus(source, audioIndex);
        }
        return new AudioFixStatus(AudioFixJobState.NOT_PREPARED, 0, "Not prepared", key);
    }

    public AudioFixService.ServeResult resolvePlayback(Path source, int audioIndex) throws IOException, InterruptedException {
        if (!ffmpegManager.isAvailable() || resolveEncoderName() == null) {
            return new AudioFixService.ServeResult(
                    Optional.empty(),
                    false,
                    failedStatus(source, audioIndex, NO_ENCODER_MESSAGE));
        }
        Path cached = cacheFile(source, audioIndex);
        if (Files.isRegularFile(cached)) {
            touch(cached);
            return new AudioFixService.ServeResult(
                    Optional.of(cached), false, readyStatus(source, audioIndex));
        }
        return new AudioFixService.ServeResult(
                Optional.empty(), false, statusFor(source, audioIndex, false));
    }

    private void enqueue(
            Path source,
            ProbedMedia media,
            int audioIndex,
            boolean viewerInitiated,
            boolean hostInitiated) throws IOException {
        String key = jobKey(source, audioIndex);
        jobs.computeIfAbsent(key, k -> new Job(source, audioIndex, media));
        Job job = jobs.get(key);
        if (Files.isRegularFile(cacheFile(source, audioIndex))) {
            job.state = AudioFixJobState.READY;
            job.progress = 100;
            return;
        }
        job.viewerInitiated = job.viewerInitiated || viewerInitiated;
        job.hostInitiated = job.hostInitiated || hostInitiated;
        if (job.state == AudioFixJobState.RUNNING) {
            if (viewerInitiated) {
                job.lastPollMs = System.currentTimeMillis();
            }
            return;
        }
        job.state = AudioFixJobState.QUEUED;
        job.message = "Queued";
        job.enqueuedAt = System.nanoTime();
        if (viewerInitiated) {
            job.lastPollMs = System.currentTimeMillis();
        }
        worker.submit(this::drainQueue);
        notifyProgressThrottled();
    }

    private void notifyProgressThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastProgressNotifyMs < 400) {
            return;
        }
        lastProgressNotifyMs = now;
        for (Runnable r : progressListeners) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    private void drainQueue() {
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        try {
            while (true) {
                Optional<Map.Entry<String, Job>> next = jobs.entrySet().stream()
                        .filter(e -> e.getValue().state == AudioFixJobState.QUEUED)
                        .sorted(Comparator.comparingLong(e -> e.getValue().enqueuedAt))
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

    private void runJob(String key, Job job) {
        Path out;
        try {
            out = cacheFile(job.source, job.audioIndex);
        } catch (IOException e) {
            job.fail(e.getMessage());
            return;
        }
        Path part = Path.of(out.getParent().toString(), out.getFileName() + ".part");
        try {
            if (!ensureDiskSpace()) {
                job.fail("Not enough free disk space for compatibility cache (need at least 512 MB free)");
                return;
            }
            evictIfNeeded();
            String encoder = resolveEncoderName();
            if (encoder == null) {
                job.fail(NO_ENCODER_MESSAGE);
                return;
            }
            job.state = AudioFixJobState.RUNNING;
            job.progress = 0;
            job.message = "Preparing compatible version";
            job.lastPollMs = System.currentTimeMillis();
            double durationSec = probeDuration(job.source);
            int maxH = settingsStore.getSettings().getCompatMaxHeight();
            boolean tonemap = job.media.video() != null && job.media.video().isHdr()
                    && CompatTranscodePlanner.encoderLikelySupportsTonemap(encoder);
            if (job.media.video() != null && job.media.video().isHdr() && !tonemap) {
                log.warn("Crewcast compat: HDR tone-map filters unavailable; output may look flat on {}", job.source);
            }
            List<String> cmd = CompatTranscodePlanner.buildCommand(
                    ffmpegManager.ffmpeg(),
                    job.source,
                    job.media,
                    job.audioIndex,
                    part,
                    encoder,
                    maxH,
                    tonemap);
            log.info("Crewcast compat ffmpeg: {}", String.join(" ", cmd));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            runningProcess = process;
            runningKey = key;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("out_time_us=")) {
                        long us = parseLongSuffix(line);
                        if (durationSec > 0) {
                            int pct = (int) Math.min(99, (us / 1_000_000.0) / durationSec * 100);
                            job.progress = Math.max(job.progress, pct);
                            job.message = "Preparing compatible version… " + job.progress + "%";
                            notifyProgressThrottled();
                        }
                    } else if (line.startsWith("progress=end")) {
                        job.progress = 100;
                    }
                }
            }
            int code = process.waitFor();
            runningProcess = null;
            runningKey = null;
            if (job.state != AudioFixJobState.RUNNING) {
                Files.deleteIfExists(part);
                return;
            }
            if (code != 0 || !Files.isRegularFile(part) || Files.size(part) < 1024) {
                Files.deleteIfExists(part);
                job.fail("Compatibility conversion failed (ffmpeg exit " + code + ")");
                return;
            }
            CrewcastWriteGuard.check(out);
            Files.move(part, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            touch(out);
            job.state = AudioFixJobState.READY;
            job.progress = 100;
            job.message = "Ready";
            log.info("Crewcast compat cache ready: {}", out);
            notifyProgressThrottled();
        } catch (Exception ex) {
            job.fail(ex.getMessage());
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
            }
        } finally {
            runningProcess = null;
            runningKey = null;
            worker.submit(this::drainQueue);
        }
    }

    private String resolveEncoderName() throws IOException, InterruptedException {
        if (resolvedEncoder != null) {
            return resolvedEncoder;
        }
        H264EncoderDetector detector = new H264EncoderDetector(ffmpegManager.ffmpeg());
        resolvedEncoder = detector.resolveEncoder();
        return resolvedEncoder;
    }

    /** Visible for tests. */
    void resetEncoderCache() {
        resolvedEncoder = null;
    }

    private void cancelIdleJobs() {
        long now = System.currentTimeMillis();
        for (Job job : jobs.values()) {
            if (job.hostInitiated || !job.viewerInitiated || job.state != AudioFixJobState.RUNNING) {
                continue;
            }
            if (now - job.lastPollMs > IDLE_CANCEL_MS) {
                cancelJob(job);
            }
        }
    }

    private void cancelJob(Job job) {
        if (runningKey != null && runningKey.equals(jobKeySafe(job))) {
            Process p = runningProcess;
            if (p != null) {
                p.destroyForcibly();
            }
        }
        jobs.values().removeIf(j -> j == job);
    }

    private String jobKeySafe(Job job) {
        try {
            return jobKey(job.source, job.audioIndex);
        } catch (IOException e) {
            return "";
        }
    }

    public void cancelAll(boolean killProcess) {
        if (killProcess) {
            Process p = runningProcess;
            if (p != null) {
                p.destroyForcibly();
            }
        }
        jobs.clear();
    }

    private void touchPoll(Path source, int audioIndex) throws IOException {
        Job job = jobs.get(jobKey(source, audioIndex));
        if (job != null) {
            job.lastPollMs = System.currentTimeMillis();
        }
    }

    private AudioFixStatus readyStatus(Path source, int audioIndex) throws IOException {
        return new AudioFixStatus(AudioFixJobState.READY, 100, "Ready", jobKey(source, audioIndex));
    }

    private AudioFixStatus failedStatus(Path source, int audioIndex, String msg) throws IOException {
        return new AudioFixStatus(AudioFixJobState.FAILED, 0, msg, jobKey(source, audioIndex));
    }

    private Path cacheFile(Path source, int audioIndex) throws IOException {
        int maxH = settingsStore.getSettings().getCompatMaxHeight();
        return CrewcastPaths.cacheDir().resolve(CompatCacheKeys.cacheFileName(source, audioIndex, maxH));
    }

    private String jobKey(Path source, int audioIndex) throws IOException {
        return CompatCacheKeys.cacheFileName(source, audioIndex, settingsStore.getSettings().getCompatMaxHeight())
                .replace(".mp4", "");
    }

    private boolean ensureDiskSpace() throws IOException {
        FileStore store = Files.getFileStore(CrewcastPaths.cacheDir());
        return store.getUsableSpace() >= MIN_FREE_BYTES;
    }

    private void evictIfNeeded() throws IOException {
        long cap = (long) settingsStore.getSettings().getAudioCacheMaxGb() * 1024L * 1024L * 1024L;
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(CrewcastPaths.cacheDir())) {
            for (Path p : stream.toList()) {
                if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".mp4")) {
                    files.add(p);
                }
            }
        }
        long total = files.stream().mapToLong(p -> {
            try {
                return Files.size(p);
            } catch (IOException e) {
                return 0;
            }
        }).sum();
        if (total <= cap) {
            return;
        }
        files.sort(Comparator.comparingLong(p -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return 0;
            }
        }));
        for (Path p : files) {
            total = files.stream().mapToLong(f -> sizeOf(f)).sum();
            if (total <= cap) {
                break;
            }
            Files.deleteIfExists(p);
        }
    }

    private static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    private void touch(Path file) throws IOException {
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
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

    private static long parseLongSuffix(String line) {
        int i = line.indexOf('=');
        if (i < 0) {
            return 0;
        }
        try {
            return Long.parseLong(line.substring(i + 1).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static final class Job {
        final Path source;
        final int audioIndex;
        final ProbedMedia media;
        volatile AudioFixJobState state = AudioFixJobState.QUEUED;
        volatile int progress;
        volatile String message = "Queued";
        volatile boolean viewerInitiated;
        volatile boolean hostInitiated;
        volatile long enqueuedAt = System.nanoTime();
        volatile long lastPollMs = System.currentTimeMillis();

        Job(Path source, int audioIndex, ProbedMedia media) {
            this.source = source.toAbsolutePath().normalize();
            this.audioIndex = audioIndex;
            this.media = media;
        }

        AudioFixStatus toStatus(String key) {
            return new AudioFixStatus(state, progress, message, key);
        }

        void fail(String msg) {
            state = AudioFixJobState.FAILED;
            message = msg != null ? msg : "Failed";
        }
    }
}
