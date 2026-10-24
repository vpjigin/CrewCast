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

    private final FfmpegManager ffmpegManager;
    private final AudioFixService audioFixService;
    private final SettingsStore settingsStore;
    private final ShareRegistry shareRegistry;

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Runnable> progressListeners = new CopyOnWriteArrayList<>();
    private volatile long lastProgressNotifyMs;
    private volatile java.util.concurrent.ScheduledFuture<?> pendingProgressNotify;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "compat-fix-worker");
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
    void startup() throws IOException {
        ConversionPartFiles.deleteStaleCompatParts(CrewcastPaths.cacheDir());
    }

    @PreDestroy
    void shutdown() {
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
        cancelViewerJobs(true);
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
        return prepareCompat(source, audioIndex, true, null);
    }

    public AudioFixStatus prepareCompat(Path source, int audioIndex, boolean viewerInitiated)
            throws IOException, InterruptedException {
        return prepareCompat(source, audioIndex, viewerInitiated, null);
    }

    public AudioFixStatus prepareCompat(Path source, int audioIndex, boolean viewerInitiated, String viewerLabel)
            throws IOException, InterruptedException {
        return prepareCompat(source, audioIndex, viewerInitiated, viewerLabel, null);
    }

    public AudioFixStatus prepareCompat(
            Path source,
            int audioIndex,
            boolean viewerInitiated,
            String viewerLabel,
            String clientId)
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
        int audio = audioFixService.resolveAudioIndex(media, audioIndex);
        if (Files.isRegularFile(cacheFile(source, audio, media))) {
            return readyStatus(source, audio, media);
        }
        String key = jobKey(source, audio, media);
        Job existing = jobs.get(key);
        if (viewerInitiated && (existing == null || existing.state == AudioFixJobState.FAILED)) {
            if (countViewerQueuedJobs() >= ConversionLimits.MAX_VIEWER_QUEUED_JOBS) {
                return failedStatus(source, audio, ConversionLimits.HOST_BUSY_MESSAGE);
            }
        }
        enqueue(source, media, audio, viewerInitiated, !viewerInitiated, viewerLabel, clientId);
        notifyProgressThrottled();
        return statusFor(source, audio);
    }

    public AudioFixStatus prepareHostDefaultTrack(Path source) throws IOException, InterruptedException {
        ProbedMedia media = audioFixService.probe(source);
        int audio = audioFixService.resolveAudioIndex(media, null);
        return prepareCompat(source, audio, false);
    }

    public void cancelForVideo(Path source) throws IOException, InterruptedException {
        ProbedMedia media = audioFixService.probe(source);
        int audio = audioFixService.resolveAudioIndex(media, null);
        String key = jobKey(source, audio, media);
        Job job = jobs.get(key);
        if (job != null && (job.state == AudioFixJobState.QUEUED || job.state == AudioFixJobState.RUNNING)) {
            cancelJob(job);
            notifyProgressThrottled();
        }
    }

    public CompatUiState uiStateForDefaultTrack(Path source) throws IOException, InterruptedException {
        ProbedMedia media = audioFixService.probe(source);
        int audio = audioFixService.resolveAudioIndex(media, null);
        PrepareCompatMode mode = settingsStore.getSettings().getPrepareCompatInAdvance();
        boolean cacheReady = Files.isRegularFile(cacheFile(source, audio));
        AudioFixStatus st = statusFor(source, audio, false);
        return CompatStatusMapper.map(mode, media, isAvailable(), st, cacheReady);
    }

    public String tooltipForDefaultTrack(Path source) throws IOException, InterruptedException {
        CompatUiState state = uiStateForDefaultTrack(source);
        ProbedMedia media = audioFixService.probe(source);
        int audio = audioFixService.resolveAudioIndex(media, null);
        AudioFixStatus st = statusFor(source, audio, false);
        return describeUiState(state, st.progress(), st.message());
    }

    public int progressForDefaultTrack(Path source) throws IOException, InterruptedException {
        ProbedMedia media = audioFixService.probe(source);
        int audio = audioFixService.resolveAudioIndex(media, null);
        return statusFor(source, audio, false).progress();
    }

    public static String describeUiState(CompatUiState state, int progress, String detail) {
        return switch (state) {
            case NOT_NEEDED -> "TV version: not needed (already browser-safe)";
            case NEEDED -> "TV version: not prepared yet";
            case QUEUED -> "TV version: waiting in queue";
            case CONVERTING -> detail != null && detail.startsWith("Finishing")
                    ? detail
                    : "TV version: converting… " + progress + "%";
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
        enqueue(video, audioFixService.probe(video), audio, false, true, null, null);
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

    public AudioFixStatus statusFor(Path source, int audioIndex) throws IOException {
        return statusFor(source, audioIndex, false);
    }

    public AudioFixStatus statusFor(Path source, Integer audioQuery) throws IOException {
        ProbedMedia media = probeMedia(source);
        int audio = audioFixService.resolveAudioIndexStrict(media, audioQuery);
        return statusFor(source, media, audio, false);
    }

    public AudioFixStatus statusFor(Path source, int audioIndex, boolean unusedRecordPoll) throws IOException {
        ProbedMedia media = probeMedia(source);
        int audio = audioFixService.resolveAudioIndex(media, audioIndex);
        return statusFor(source, media, audio, unusedRecordPoll);
    }

    private AudioFixStatus statusFor(
            Path source, ProbedMedia media, int audioIndex, boolean unusedRecordPoll) throws IOException {
        String key = jobKey(source, audioIndex, media);
        Job job = jobs.get(key);
        if (job != null && job.state != AudioFixJobState.FAILED) {
            if (job.state == AudioFixJobState.READY || Files.isRegularFile(cacheFile(source, audioIndex, media))) {
                return readyStatus(source, audioIndex, media);
            }
            return job.toStatus(key, queueAheadFor(source, audioIndex));
        }
        if (Files.isRegularFile(cacheFile(source, audioIndex, media))) {
            return readyStatus(source, audioIndex, media);
        }
        return new AudioFixStatus(
                AudioFixJobState.NOT_PREPARED, 0, "Not prepared", key,
                null, null, null, null, null, null);
    }

    public AudioFixService.ServeResult resolvePlayback(Path source, Integer audioQuery)
            throws IOException, InterruptedException {
        ProbedMedia media = audioFixService.probe(source);
        int audio = audioFixService.resolveAudioIndexStrict(media, audioQuery);
        return resolvePlayback(source, media, audio);
    }

    public AudioFixService.ServeResult resolvePlayback(Path source, int audioIndex) throws IOException, InterruptedException {
        ProbedMedia media = audioFixService.probe(source);
        int audio = audioFixService.resolveAudioIndex(media, audioIndex);
        return resolvePlayback(source, media, audio);
    }

    private AudioFixService.ServeResult resolvePlayback(Path source, ProbedMedia media, int audioIndex)
            throws IOException, InterruptedException {
        if (!ffmpegManager.isAvailable() || resolveEncoderName() == null) {
            return new AudioFixService.ServeResult(
                    Optional.empty(),
                    false,
                    failedStatus(source, audioIndex, NO_ENCODER_MESSAGE));
        }
        Path cached = cacheFile(source, audioIndex, media);
        if (Files.isRegularFile(cached)) {
            touch(cached);
            return new AudioFixService.ServeResult(
                    Optional.of(cached), false, readyStatus(source, audioIndex, media));
        }
        return new AudioFixService.ServeResult(
                Optional.empty(), false, statusFor(source, media, audioIndex, false));
    }

    public int countRunningJobs() {
        return (int) jobs.values().stream().filter(j -> j.state == AudioFixJobState.RUNNING).count();
    }

    public int countQueuedJobs() {
        return (int) jobs.values().stream().filter(j -> j.state == AudioFixJobState.QUEUED).count();
    }

    public int countVideosNeedingConversion() throws IOException {
        int n = 0;
        for (Path video : shareRegistry.listVideoPaths()) {
            try {
                if (needsConversionAndNotActive(video)) {
                    n++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return n;
    }

    private boolean needsConversionAndNotActive(Path video) throws IOException, InterruptedException {
        ProbedMedia media = audioFixService.probe(video);
        if (!CompatPlaybackPolicy.videoNeedsCompatTranscode(media)) {
            return false;
        }
        int audio = audioFixService.defaultAudioIndex(media);
        if (Files.isRegularFile(cacheFile(video, audio))) {
            return false;
        }
        CompatUiState ui = uiStateForDefaultTrack(video);
        return ui == CompatUiState.NEEDED || ui == CompatUiState.FAILED;
    }

    /** Viewer-initiated jobs waiting to start; {@code RUNNING} jobs are not counted toward the cap. */
    private int countViewerQueuedJobs() {
        return (int) jobs.values().stream()
                .filter(j -> j.state == AudioFixJobState.QUEUED
                        && j.startedBy == ConversionStartedBy.VIEWER
                        && !j.hostInitiated)
                .count();
    }

    private void enqueue(
            Path source,
            ProbedMedia media,
            int audioIndex,
            boolean viewerInitiated,
            boolean hostInitiated,
            String viewerLabel,
            String clientId) throws IOException {
        String key = jobKey(source, audioIndex, media);
        jobs.computeIfAbsent(key, k -> new Job(source, audioIndex, media));
        Job job = jobs.get(key);
        if (Files.isRegularFile(cacheFile(source, audioIndex, media))) {
            job.state = AudioFixJobState.READY;
            job.progress = 100;
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
            return;
        }
        job.state = AudioFixJobState.QUEUED;
        job.message = "Queued";
        job.enqueuedAt = System.nanoTime();
        worker.submit(this::drainQueue);
        notifyProgressThrottled();
    }

    private void notifyProgressThrottled() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastProgressNotifyMs;
        if (elapsed >= 400) {
            fireProgressListeners();
            return;
        }
        if (pendingProgressNotify == null || pendingProgressNotify.isDone()) {
            pendingProgressNotify = autoPrepareScheduler.schedule(
                    this::fireProgressListeners, 400 - elapsed, TimeUnit.MILLISECONDS);
        }
    }

    private void fireProgressListeners() {
        lastProgressNotifyMs = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            for (Job j : jobs.values()) {
                if (j.state == AudioFixJobState.QUEUED || j.state == AudioFixJobState.RUNNING) {
                    log.debug(
                            "Compat job notify: path={} audio={} state={} progress={}% speed={} eta={}s key={}",
                            j.source,
                            j.audioIndex,
                            j.state,
                            j.progress,
                            j.speed,
                            j.etaSeconds,
                            jobKeySafe(j));
                }
            }
        }
        for (Runnable r : progressListeners) {
            try {
                r.run();
            } catch (Exception ex) {
                log.warn("Compat progress listener failed", ex);
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
            out = cacheFile(job.source, job.audioIndex, job.media);
        } catch (IOException e) {
            job.fail(e.getMessage());
            return;
        }
        Path part = ConversionPartFiles.partPath(out);
        job.outputPath = out;
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
            job.speed = null;
            job.etaSeconds = null;
            notifyProgressThrottled();
            double durationSec = resolveDurationSec(job.source, job.media);
            job.durationSec = durationSec;
            if (durationSec <= 0) {
                job.fail("Cannot probe video duration (required for TV version conversion)");
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug(
                        "Compat job duration: path={} durationSec={} (format={} probe={})",
                        job.source,
                        durationSec,
                        job.media.formatDurationSec(),
                        probeDuration(job.source));
            }
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
                FfmpegProgressParser.ProgressState pState = new FfmpegProgressParser.ProgressState(job.progress);
                pState.speed = job.speed;
                String line;
                while ((line = reader.readLine()) != null) {
                    int prevProgress = job.progress;
                    Double prevSpeed = job.speed;
                    Integer prevEta = job.etaSeconds;
                    boolean prevFinishing = job.finishing;
                    FfmpegProgressParser.Snapshot snap =
                            FfmpegProgressParser.applyLine(line, job.durationSec, pState);
                    pState.progress = snap.progress;
                    pState.finishing = snap.finishing;
                    job.progress = snap.progress;
                    job.finishing = snap.finishing;
                    if (snap.speed != null && snap.speed > 0) {
                        job.speed = snap.speed;
                        pState.speed = snap.speed;
                    }
                    if (snap.etaSeconds != null) {
                        job.etaSeconds = snap.etaSeconds;
                    } else if (job.speed != null && job.speed > 0 && job.durationSec > 0 && job.progress < 99 && !job.finishing) {
                        double outSec = (job.progress / 100.0) * job.durationSec;
                        job.etaSeconds = (int) Math.ceil(Math.max(0, job.durationSec - outSec) / job.speed);
                    }
                    boolean changed = job.progress != prevProgress
                            || job.finishing != prevFinishing
                            || (job.speed != null && !job.speed.equals(prevSpeed))
                            || (job.etaSeconds != null && !job.etaSeconds.equals(prevEta));
                    if (log.isDebugEnabled()
                            && (line.startsWith("out_time") || line.startsWith("speed=") || line.startsWith("progress="))) {
                        log.debug(
                                "Compat ffmpeg progress: path={} line={} progress={}% finishing={} speed={} eta={}s durationSec={}",
                                job.source,
                                line,
                                job.progress,
                                job.finishing,
                                job.speed,
                                job.etaSeconds,
                                job.durationSec);
                    }
                    if (changed) {
                        job.message = job.finishing
                                ? "Finishing up…"
                                : "Preparing compatible version… " + job.progress + "%";
                        notifyProgressThrottled();
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

    /** Package tests: enqueue without API guardrails. */
    void testEnqueue(Path source, ProbedMedia media, int audioIndex, boolean viewerInitiated, String viewerLabel)
            throws IOException {
        enqueue(source, media, audioIndex, viewerInitiated, !viewerInitiated, viewerLabel, null);
    }

    /** Package tests: set job state without starting the worker (no ffmpeg). */
    void testInstallJob(Path source, ProbedMedia media, int audioIndex, AudioFixJobState state, int progress)
            throws IOException {
        String key = jobKey(source, audioIndex, media);
        Job job = jobs.computeIfAbsent(key, k -> new Job(source, audioIndex, media));
        job.state = state;
        job.progress = progress;
        job.message = state == AudioFixJobState.QUEUED ? "Queued" : "Preparing compatible version… " + progress + "%";
        job.startedBy = ConversionStartedBy.HOST;
        job.hostInitiated = true;
        job.viewerInitiated = false;
    }

    int activeJobCount() {
        return jobs.size();
    }

    int viewerQueuedCount() {
        return countViewerQueuedJobs();
    }

    void testSetResolvedEncoder(String encoder) {
        resolvedEncoder = encoder;
    }

    void testSetJobProgress(Path source, int audioIndex, int progress) throws IOException {
        Job job = jobs.get(jobKey(source, audioIndex));
        if (job != null) {
            job.progress = progress;
            return;
        }
        ProbedMedia media = probeMedia(source);
        job = jobs.get(jobKey(source, audioIndex, media));
        if (job != null) {
            job.progress = progress;
        }
    }

    /** Package tests: invoke progress listeners without waiting for throttle. */
    void testFireProgressListeners() {
        fireProgressListeners();
    }

    void testSetJobState(Path source, int audioIndex, AudioFixJobState state) throws IOException {
        Job job = jobs.get(jobKey(source, audioIndex));
        if (job != null) {
            job.state = state;
            return;
        }
        ProbedMedia media = probeMedia(source);
        job = jobs.get(jobKey(source, audioIndex, media));
        if (job != null) {
            job.state = state;
        }
    }

    private void cancelJob(Job job) {
        String key = jobKeySafe(job);
        boolean running = runningKey != null && runningKey.equals(key);
        Process p = running ? runningProcess : null;
        if (p != null) {
            p.destroyForcibly();
        }
        jobs.values().removeIf(j -> j == job);
        if (p != null) {
            try {
                p.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (job.outputPath != null) {
            ConversionPartFiles.deleteIfExists(job.outputPath);
        } else {
            try {
                ConversionPartFiles.deleteIfExists(cacheFile(job.source, job.audioIndex, job.media));
            } catch (IOException ignored) {
            }
        }
    }

    private String jobKeySafe(Job job) {
        try {
            return jobKey(job.source, job.audioIndex, job.media);
        } catch (IOException e) {
            return "";
        }
    }

    public void cancelAll(boolean killProcess) {
        Process p = killProcess ? runningProcess : null;
        if (p != null) {
            p.destroyForcibly();
        }
        for (Job job : new ArrayList<>(jobs.values())) {
            if (job.outputPath != null) {
                ConversionPartFiles.deleteIfExists(job.outputPath);
            }
        }
        if (p != null) {
            try {
                p.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        jobs.clear();
    }

    /** Stops viewer-started jobs when sharing ends; host prep continues. */
    public void cancelViewerJobs(boolean killProcess) {
        List<Job> toCancel = jobs.values().stream()
                .filter(j -> j.viewerInitiated || j.startedBy == ConversionStartedBy.VIEWER)
                .filter(j -> j.state == AudioFixJobState.QUEUED || j.state == AudioFixJobState.RUNNING)
                .toList();
        for (Job job : toCancel) {
            cancelJob(job);
        }
        notifyProgressThrottled();
    }

    private AudioFixStatus readyStatus(Path source, int audioIndex, ProbedMedia media) throws IOException {
        return new AudioFixStatus(
                AudioFixJobState.READY, 100, "Ready", jobKey(source, audioIndex, media),
                null, null, null, null, null, null);
    }

    private AudioFixStatus readyStatus(Path source, int audioIndex) throws IOException {
        return readyStatus(source, audioIndex, probeMedia(source));
    }

    private ProbedMedia probeMedia(Path source) throws IOException {
        try {
            return audioFixService.probe(source);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while probing media", e);
        }
    }

    private AudioFixStatus failedStatus(Path source, int audioIndex, String msg) throws IOException {
        return new AudioFixStatus(
                AudioFixJobState.FAILED, 0, msg, jobKey(source, audioIndex),
                null, null, null, null, null, null);
    }

    private Path cacheFile(Path source, int audioIndex, ProbedMedia media) throws IOException {
        int maxH = settingsStore.getSettings().getCompatMaxHeight();
        return CrewcastPaths.cacheDir().resolve(CompatCacheKeys.cacheFileName(source, audioIndex, maxH));
    }

    private Path cacheFile(Path source, int audioIndex) throws IOException {
        return cacheFile(source, audioIndex, probeMedia(source));
    }

    private String jobKey(Path source, int audioIndex, ProbedMedia media) throws IOException {
        int maxH = settingsStore.getSettings().getCompatMaxHeight();
        return CompatCacheKeys.cacheFileName(source, audioIndex, maxH).replace(".mp4", "");
    }

    private String jobKey(Path source, int audioIndex) throws IOException {
        return jobKey(source, audioIndex, probeMedia(source));
    }

    private boolean ensureDiskSpace() throws IOException {
        FileStore store = Files.getFileStore(CrewcastPaths.cacheDir());
        return store.getUsableSpace() >= MIN_FREE_BYTES;
    }

    private void evictIfNeeded() throws IOException {
        long cap = (long) settingsStore.getSettings().getAudioCacheMaxGb() * 1024L * 1024L * 1024L;
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

    /**
     * Rough prep-time guess from file duration (~2× realtime GPU transcode). Empty when unknown.
     */
    public Integer estimatedPrepareMinutesFor(Path source) {
        double d = probeDuration(source);
        if (d <= 0) {
            return null;
        }
        return Math.max(1, (int) Math.round(d / 120.0));
    }

    private double resolveDurationSec(Path file, ProbedMedia media) {
        double d = probeDuration(file);
        if (d > 0) {
            return d;
        }
        if (media != null && media.formatDurationSec() > 0) {
            return media.formatDurationSec();
        }
        return probeVideoStreamDuration(file);
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

    private double probeVideoStreamDuration(Path file) {
        try {
            Process p = new ProcessBuilder(
                    ffmpegManager.ffprobe().toString(),
                    "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=duration",
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

    public int queueAheadFor(Path source, int audioIndex) throws IOException {
        String key = jobKey(source, audioIndex);
        Job job = jobs.get(key);
        if (job == null || job.state != AudioFixJobState.QUEUED) {
            return 0;
        }
        long mine = job.enqueuedAt;
        return (int) jobs.values().stream()
                .filter(j -> j.state == AudioFixJobState.QUEUED && j.enqueuedAt < mine)
                .count();
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
        volatile ConversionStartedBy startedBy;
        volatile String startedByLabel;
        volatile String startedByClientId;
        volatile Double speed;
        volatile Integer etaSeconds;
        volatile double durationSec;
        volatile boolean finishing;
        volatile Path outputPath;

        Job(Path source, int audioIndex, ProbedMedia media) {
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
