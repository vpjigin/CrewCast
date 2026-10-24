package com.solocrew.crewcast.facade;

import com.solocrew.crewcast.media.AudioFixJobState;
import com.solocrew.crewcast.media.AudioFixService;
import com.solocrew.crewcast.media.AudioFixStatus;
import com.solocrew.crewcast.media.CompatFixService;
import com.solocrew.crewcast.media.CompatUiState;
import com.solocrew.crewcast.share.VideoFile;
import com.solocrew.crewcast.media.FfmpegDownloadAssets;
import com.solocrew.crewcast.media.FfmpegDownloadListener;
import com.solocrew.crewcast.media.FfmpegDownloader;
import com.solocrew.crewcast.net.DownloadFailureDetails;
import com.solocrew.crewcast.net.DownloadErrors;
import com.solocrew.crewcast.system.CrewcastDiagnostics;
import com.solocrew.crewcast.system.CrewcastPaths;
import com.solocrew.crewcast.media.FfmpegManager;
import com.solocrew.crewcast.media.VideoBrowserContainers;
import com.solocrew.crewcast.media.ProbedMedia;
import com.solocrew.crewcast.media.ShareVideoDetailsMeta;
import com.solocrew.crewcast.media.SubtitleService;
import com.solocrew.crewcast.settings.FfmpegDownloadConsent;
import com.solocrew.crewcast.access.AccessSession;
import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.settings.ShareEntry;
import com.solocrew.crewcast.share.ShareExposeFilter;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.system.KeepAwakeManager;
import com.solocrew.crewcast.system.LanIpDetector;
import com.solocrew.crewcast.tunnel.CloudflaredListener;
import com.solocrew.crewcast.tunnel.CloudflaredManager;
import com.solocrew.crewcast.viewers.ViewerRecord;
import com.solocrew.crewcast.viewers.ViewerRegistry;
import com.solocrew.crewcast.web.ServerPortHolder;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SharingService implements CloudflaredListener, FfmpegDownloadListener {

    private static final Logger log = LoggerFactory.getLogger(SharingService.class);

    private final ShareRegistry shareRegistry;
    private final SettingsStore settingsStore;
    private final AccessSession accessSession;
    private final SharingState sharingState;
    private final ViewerRegistry viewerRegistry;
    private final CloudflaredManager cloudflaredManager;
    private final KeepAwakeManager keepAwakeManager;
    private final ServerPortHolder portHolder;
    private final AudioFixService audioFixService;
    private final CompatFixService compatFixService;
    private final FfmpegManager ffmpegManager;
    private final FfmpegDownloader ffmpegDownloader;
    private final SubtitleService subtitleService;

    private final List<SharingUiListener> listeners = new CopyOnWriteArrayList<>();

    public SharingService(
            ShareRegistry shareRegistry,
            SettingsStore settingsStore,
            AccessSession accessSession,
            SharingState sharingState,
            ViewerRegistry viewerRegistry,
            CloudflaredManager cloudflaredManager,
            KeepAwakeManager keepAwakeManager,
            ServerPortHolder portHolder,
            AudioFixService audioFixService,
            CompatFixService compatFixService,
            FfmpegManager ffmpegManager,
            FfmpegDownloader ffmpegDownloader,
            SubtitleService subtitleService) {
        this.shareRegistry = shareRegistry;
        this.settingsStore = settingsStore;
        this.accessSession = accessSession;
        this.sharingState = sharingState;
        this.viewerRegistry = viewerRegistry;
        this.cloudflaredManager = cloudflaredManager;
        this.keepAwakeManager = keepAwakeManager;
        this.portHolder = portHolder;
        this.audioFixService = audioFixService;
        this.compatFixService = compatFixService;
        this.ffmpegManager = ffmpegManager;
        this.ffmpegDownloader = ffmpegDownloader;
        this.subtitleService = subtitleService;
    }

    @PostConstruct
    void init() {
        cloudflaredManager.addListener(this);
        ffmpegDownloader.addListener(this);
        cloudflaredManager.setProtocol(settingsStore.getSettings().getTunnelProtocol());
        compatFixService.addProgressListener(this::notifyCompatPrepChanged);
    }

    private void notifyCompatPrepChanged() {
        for (SharingUiListener l : listeners) {
            l.onCompatPrepChanged();
        }
    }

    public FfmpegDownloadAssets.Plan ffmpegDownloadPlan() {
        return ffmpegDownloader.planForCurrentHost();
    }

    public void downloadFfmpegInBackground() {
        if (ffmpegDownloader.isDownloadInProgress()) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                ffmpegDownloader.downloadIfNeeded(true);
                ffmpegManager.recheck();
            } catch (Exception ex) {
                DownloadFailureDetails d = ffmpegDownloader.lastFailure();
                String msg = d != null ? d.reason() : DownloadErrors.plainReason(ex, null);
                onFfmpegDownloadFinished(false, msg);
            }
        }, "ffmpeg-download");
        t.setDaemon(true);
        t.start();
    }

    public boolean isFfmpegDownloadInProgress() {
        return ffmpegDownloader.isDownloadInProgress();
    }

    public DownloadFailureDetails lastFfmpegDownloadFailure() {
        return ffmpegDownloader.lastFailure();
    }

    public DownloadFailureDetails lastTunnelFailure() {
        return cloudflaredManager.lastFailure();
    }

    public String runDiagnosticsReport() {
        return CrewcastDiagnostics.runReport();
    }

    public Path logsDirectory() {
        return CrewcastPaths.logsDir();
    }

    public boolean addedShareIncludesNonNativeVideo(Path path, ShareEntry.Kind kind) throws IOException {
        return VideoBrowserContainers.addedShareIncludesNonNativeVideo(path, kind);
    }

    public void downloadFfmpegBlocking() throws Exception {
        AppSettings s = settingsStore.getSettings();
        s.setFfmpegDownloadConsent(FfmpegDownloadConsent.ALLOWED);
        settingsStore.update(s);
        ffmpegDownloader.downloadIfNeeded(true);
        ffmpegManager.recheck();
    }

    @Override
    public void onFfmpegDownloadProgress(int percent, String message) {
        for (SharingUiListener l : listeners) {
            l.onFfmpegDownloadProgress(percent, message);
        }
    }

    @Override
    public void onFfmpegDownloadFinished(boolean success, String message) {
        ffmpegManager.recheck();
        for (SharingUiListener l : listeners) {
            l.onFfmpegDownloadFinished(success, message);
        }
    }

    @EventListener
    public void onWebServerReady(WebServerInitializedEvent event) {
        notifyServerReady(event.getWebServer().getPort());
    }

    public void addListener(SharingUiListener listener) {
        listeners.add(listener);
        if (portHolder.isReady()) {
            listener.onServerReady(portHolder.getPort());
        }
        listener.onSharingChanged(sharingState.isSharingActive());
    }

    public void removeListener(SharingUiListener listener) {
        listeners.remove(listener);
    }

    public int getServerPort() {
        return portHolder.getPort();
    }

    public boolean isServerReady() {
        return portHolder.isReady();
    }

    public boolean isSharingActive() {
        return sharingState.isSharingActive();
    }

    public TunnelState getTunnelState() {
        return mapTunnelState(cloudflaredManager.getState());
    }

    public boolean isSupportedVideoFileName(String name) {
        return ShareRegistry.isVideoFileName(name);
    }

    public boolean isKeepAwakeActive() {
        return keepAwakeManager.isActive();
    }

    public AppSettings getSettings() {
        return settingsStore.getSettings();
    }

    public SettingsStore getSettingsStore() {
        return settingsStore;
    }

    public void saveSettings(AppSettings settings) {
        settingsStore.update(settings);
        cloudflaredManager.setProtocol(settings.getTunnelProtocol());
        compatFixService.scheduleAutoPrepareScan();
    }

    public void addFolder(Path folder) throws IOException {
        shareRegistry.addFolder(folder);
        compatFixService.scheduleAutoPrepareScan();
    }

    public void addFile(Path file) throws IOException {
        shareRegistry.addFile(file);
        compatFixService.scheduleAutoPrepareScan();
    }

    public void removeShare(int index) {
        List<ShareEntry> before = shareRegistry.listShareEntries();
        if (index >= 0 && index < before.size()) {
            Path root = Path.of(before.get(index).getPath());
            audioFixService.onShareRemoved(root);
            compatFixService.onShareRemoved(root);
        }
        shareRegistry.removeShare(index);
    }

    public void removeAllShares() {
        for (ShareEntry e : shareRegistry.listShareEntries()) {
            Path root = Path.of(e.getPath());
            audioFixService.onShareRemoved(root);
            compatFixService.onShareRemoved(root);
        }
        shareRegistry.clearAllShares();
    }

    public boolean isCompatEncoderAvailable() {
        return compatFixService.isAvailable();
    }

    public void prepareCompatForShareEntry(ShareEntry entry) throws Exception {
        Path path = Path.of(entry.getPath());
        if (entry.getKind() == ShareEntry.Kind.FILE) {
            compatFixService.prepareHostDefaultTrack(path);
        } else {
            compatFixService.prepareAllNeedingUnder(path);
        }
    }

    public void prepareAllCompatForFolder(ShareEntry entry) throws Exception {
        compatFixService.prepareAllVideosUnder(Path.of(entry.getPath()));
    }

    public void cancelCompatForPath(Path videoPath) throws Exception {
        compatFixService.cancelForVideo(videoPath);
    }

    public Integer estimateCompatPrepareMinutes(java.nio.file.Path video) {
        return compatFixService.estimatedPrepareMinutesFor(video);
    }

    public ConversionQueueSummary conversionQueueSummary() throws IOException {
        return new ConversionQueueSummary(
                compatFixService.countRunningJobs(),
                compatFixService.countQueuedJobs(),
                compatFixService.countVideosNeedingConversion());
    }

    public void prepareAllNeedingCompat() throws Exception {
        for (ShareEntry e : shareRegistry.listShareEntries()) {
            prepareCompatForShareEntry(e);
        }
    }

    /**
     * Share rows without ffprobe / compat probing — for immediate UI while {@link #listShares()} runs off the EDT.
     */
    public List<ShareListItem> listSharesSkeleton() {
        List<ShareEntry> entries = shareRegistry.listShareEntries();
        List<ShareListItem> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ShareEntry e = entries.get(i);
            out.add(new ShareListItem(
                    i,
                    e,
                    describeShare(e),
                    "",
                    "",
                    false,
                    "",
                    CompatUiState.NOT_NEEDED,
                    0,
                    "",
                    false,
                    false,
                    0,
                    null,
                    null,
                    null,
                    null,
                    List.of()));
        }
        return out;
    }

    public int shareEntryCount() {
        return shareRegistry.listShareEntries().size();
    }

    public List<ShareListItem> listShares() {
        List<ShareEntry> entries = shareRegistry.listShareEntries();
        List<ShareListItem> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ShareEntry e = entries.get(i);
            out.add(buildShareListItem(i, e));
        }
        return out;
    }

    /**
     * Re-probes compat job state for existing rows (no share-registry walk beyond cached paths).
     * Used when ffmpeg progress ticks so the desktop UI is not blocked behind a full {@link #listShares()}.
     */
    public List<ShareListItem> refreshCompatFieldsOnItems(List<ShareListItem> items) {
        List<ShareListItem> out = new ArrayList<>(items.size());
        for (ShareListItem item : items) {
            out.add(refreshCompatFieldsOnItem(item));
        }
        return out;
    }

    private ShareListItem refreshCompatFieldsOnItem(ShareListItem item) {
        ShareEntry e = item.entry();
        try {
            if (e.getKind() == ShareEntry.Kind.FILE
                    && ShareRegistry.isVideoFileName(Path.of(e.getPath()).getFileName().toString())) {
                Path vp = Path.of(e.getPath());
                CompatFields f = compatFieldsForVideo(vp);
                logCompatRow(vp, f);
                return new ShareListItem(
                        item.index(),
                        item.entry(),
                        item.meta(),
                        item.audioMeta(),
                        item.subtitleMeta(),
                        item.hevcWarning(),
                        item.compatSummary(),
                        f.state(),
                        f.progress(),
                        f.tooltip(),
                        f.canPrepare(),
                        f.canCancel(),
                        f.queueAhead(),
                        f.speed(),
                        f.etaSeconds(),
                        f.startedBy(),
                        f.startedByLabel(),
                        item.folderVideos());
            }
            if (e.getKind() == ShareEntry.Kind.FOLDER && item.folderVideos() != null && !item.folderVideos().isEmpty()) {
                List<ShareVideoCompatRow> videos = new ArrayList<>();
                for (ShareVideoCompatRow row : item.folderVideos()) {
                    CompatFields f = compatFieldsForVideo(row.path());
                    logCompatRow(row.path(), f);
                    videos.add(new ShareVideoCompatRow(
                            row.path(),
                            row.displayName(),
                            f.state(),
                            f.progress(),
                            f.tooltip(),
                            f.canPrepare(),
                            f.canCancel(),
                            f.queueAhead(),
                            f.speed(),
                            f.etaSeconds(),
                            f.startedBy(),
                            f.startedByLabel()));
                }
                return new ShareListItem(
                        item.index(),
                        item.entry(),
                        item.meta(),
                        item.audioMeta(),
                        item.subtitleMeta(),
                        item.hevcWarning(),
                        item.compatSummary(),
                        item.fileCompatState(),
                        item.fileCompatProgress(),
                        item.fileCompatTooltip(),
                        item.fileCompatCanPrepare(),
                        item.fileCompatCanCancel(),
                        item.fileCompatQueueAhead(),
                        item.fileCompatSpeed(),
                        item.fileCompatEtaSeconds(),
                        item.fileCompatStartedBy(),
                        item.fileCompatStartedByLabel(),
                        videos);
            }
        } catch (Exception ex) {
            log.debug("Compat refresh failed for {}", e.getPath(), ex);
        }
        return item;
    }

    private ShareListItem buildShareListItem(int index, ShareEntry e) {
        int videos = countVideosInShare(e);
        String metaLine = describeShare(e);
        String audioMetaLine = videos > 0 ? audioFixService.summarizeForShare(e) : "";
        String compatSummary = videos > 0 ? compatFixService.summarizeForShare(e) : "";
        if (e.getKind() == ShareEntry.Kind.FILE
                && ShareRegistry.isVideoFileName(Path.of(e.getPath()).getFileName().toString())) {
            try {
                Path vp = Path.of(e.getPath());
                ProbedMedia media = audioFixService.probe(vp);
                metaLine = ShareVideoDetailsMeta.format(vp, media, audioFixService);
                audioMetaLine = "";
                compatSummary = "";
            } catch (Exception ex) {
                log.debug("Video details meta failed for {}", e.getPath(), ex);
            }
        }
        CompatUiState fileState = CompatUiState.NOT_NEEDED;
        int fileProgress = 0;
        String fileTooltip = "";
        boolean canPrepare = false;
        boolean canCancel = false;
        int fileQueueAhead = 0;
        Double fileSpeed = null;
        Integer fileEta = null;
        com.solocrew.crewcast.media.ConversionStartedBy fileStartedBy = null;
        String fileStartedByLabel = null;
        List<ShareVideoCompatRow> folderVideos = List.of();
        try {
            if (e.getKind() == ShareEntry.Kind.FILE
                    && ShareRegistry.isVideoFileName(Path.of(e.getPath()).getFileName().toString())) {
                Path vp = Path.of(e.getPath());
                CompatFields f = compatFieldsForVideo(vp);
                logCompatRow(vp, f);
                fileState = f.state();
                fileProgress = f.progress();
                fileTooltip = f.tooltip();
                canPrepare = f.canPrepare();
                canCancel = f.canCancel();
                fileQueueAhead = f.queueAhead();
                fileSpeed = f.speed();
                fileEta = f.etaSeconds();
                fileStartedBy = f.startedBy();
                fileStartedByLabel = f.startedByLabel();
            } else if (e.getKind() == ShareEntry.Kind.FOLDER) {
                folderVideos = listFolderVideoCompatRows(e);
            }
        } catch (Exception ignored) {
            fileState = CompatUiState.UNAVAILABLE;
            fileTooltip = CompatFixService.NO_ENCODER_MESSAGE;
        }
        return new ShareListItem(
                index,
                e,
                metaLine,
                audioMetaLine,
                subtitleService.summarizeForShare(e),
                audioFixService.hevcWarningForShare(e),
                compatSummary,
                fileState,
                fileProgress,
                fileTooltip,
                canPrepare,
                canCancel,
                fileQueueAhead,
                fileSpeed,
                fileEta,
                fileStartedBy,
                fileStartedByLabel,
                folderVideos);
    }

    private record CompatFields(
            CompatUiState state,
            int progress,
            String tooltip,
            boolean canPrepare,
            boolean canCancel,
            int queueAhead,
            Double speed,
            Integer etaSeconds,
            com.solocrew.crewcast.media.ConversionStartedBy startedBy,
            String startedByLabel,
            AudioFixJobState jobState,
            String cacheKey) {}

    private CompatFields compatFieldsForVideo(Path vp) throws Exception {
        CompatUiState fileState = compatFixService.uiStateForDefaultTrack(vp);
        AudioFixStatus job = compatJobStatus(vp);
        String fileTooltip = compatFixService.describeUiState(
                fileState, job.progress(), job.message());
        return new CompatFields(
                fileState,
                job.progress(),
                fileTooltip,
                canPrepareCompat(fileState, compatFixService.isAvailable()),
                canCancelCompat(fileState),
                job.queueAhead() != null ? job.queueAhead() : 0,
                job.speed(),
                job.etaSeconds(),
                job.startedBy(),
                job.startedByLabel(),
                job.status(),
                job.cacheKey());
    }

    private void logCompatRow(Path video, CompatFields f) {
        if (log.isDebugEnabled()) {
            log.debug(
                    "Share row compat: path={} uiState={} progress={}% jobState={} cacheKey={}",
                    video,
                    f.state(),
                    f.progress(),
                    f.jobState(),
                    f.cacheKey());
        }
    }

    private AudioFixStatus compatJobStatus(Path video) throws Exception {
        var media = audioFixService.probe(video);
        return compatFixService.statusFor(video, (Integer) null);
    }

    private List<ShareVideoCompatRow> listFolderVideoCompatRows(ShareEntry folder) throws Exception {
        List<ShareVideoCompatRow> rows = new ArrayList<>();
        for (Path v : shareRegistry.listVideosUnder(Path.of(folder.getPath()))) {
            CompatFields f = compatFieldsForVideo(v);
            logCompatRow(v, f);
            rows.add(new ShareVideoCompatRow(
                    v,
                    v.getFileName().toString(),
                    f.state(),
                    f.progress(),
                    f.tooltip(),
                    f.canPrepare(),
                    f.canCancel(),
                    f.queueAhead(),
                    f.speed(),
                    f.etaSeconds(),
                    f.startedBy(),
                    f.startedByLabel()));
        }
        return rows;
    }

    private static boolean canPrepareCompat(CompatUiState state, boolean encoderAvailable) {
        if (!encoderAvailable) {
            return false;
        }
        return state != CompatUiState.READY
                && state != CompatUiState.QUEUED
                && state != CompatUiState.CONVERTING;
    }

    private static boolean canCancelCompat(CompatUiState state) {
        return state == CompatUiState.QUEUED || state == CompatUiState.CONVERTING;
    }

    public boolean hasShares() {
        return shareRegistry.hasShares();
    }

    public int countVideos() {
        return shareRegistry.countVideos();
    }

    public void startSharing() {
        if (!shareRegistry.hasShares() || !portHolder.isReady()) {
            return;
        }
        sharingState.setSharingActive(true);
        accessSession.regenerateToken();
        viewerRegistry.clearOnSharingRestart();
        AppSettings settings = settingsStore.getSettings();
        if (settings.isKeepComputerAwake()) {
            keepAwakeManager.start();
        }
        if (settings.isStartTunnelWhenSharingStarts()) {
            cloudflaredManager.setProtocol(settings.getTunnelProtocol());
            cloudflaredManager.start(portHolder.getPort());
        }
        audioFixService.onSharingStarted();
        compatFixService.onSharingStarted();
        notifySharingChanged(true);
        notifyViewersChanged();
    }

    public void stopSharing() {
        sharingState.setSharingActive(false);
        cloudflaredManager.stop();
        keepAwakeManager.stop();
        audioFixService.onSharingStopped();
        compatFixService.onSharingStopped();
        notifySharingChanged(false);
        notifyViewersChanged();
    }

    public void regenerateAccessToken() {
        accessSession.regenerateToken();
    }

    public SharingLinks currentLinks() {
        String token = accessSession.getToken();
        int port = portHolder.getPort();
        String publicBase = cloudflaredManager.getPublicUrl();
        String publicLink = publicBase != null && !publicBase.isBlank()
                ? publicBase + "/?t=" + token
                : "";
        String lanLink = port > 0
                ? "http://" + LanIpDetector.primaryLanIp() + ":" + port + "/?t=" + token
                : "";
        return new SharingLinks(publicLink, lanLink, token);
    }

    public List<ViewerView> activeViewers() {
        Map<String, Integer> waitingProgress = waitingProgressByDisplayName();
        return viewerRegistry.activeViewers().stream()
                .map(v -> {
                    Integer pct = waitingProgress.get(v.fileName());
                    String detail = pct != null ? "Waiting for conversion" : null;
                    return new ViewerView(v.key(), v.device(), v.fileName(), v.viaTunnel(), pct, detail);
                })
                .toList();
    }

    private Map<String, Integer> waitingProgressByDisplayName() {
        Map<String, Integer> out = new HashMap<>();
        try {
            for (VideoFile vf : shareRegistry.listVideos()) {
                Path p = vf.path();
                var media = audioFixService.probe(p);
                int audio = audioFixService.defaultAudioIndex(media);
                AudioFixStatus compat = compatFixService.statusFor(p, audio);
                if (isWaiting(compat)) {
                    out.put(vf.displayName(), compat.progress());
                    continue;
                }
                AudioFixStatus audioSt = audioFixService.statusFor(p, audio);
                if (isWaiting(audioSt)) {
                    out.put(vf.displayName(), audioSt.progress());
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static boolean isWaiting(AudioFixStatus st) {
        return st.status() == AudioFixJobState.QUEUED || st.status() == AudioFixJobState.RUNNING;
    }

    public int activeViewerCount() {
        return viewerRegistry.activeViewerCount();
    }

    public void removeViewer(String key) {
        viewerRegistry.block(key);
        notifyViewersChanged();
    }

    public void retryTunnel() {
        if (portHolder.isReady()) {
            cloudflaredManager.retry();
        }
    }

    public void shutdown() {
        stopSharing();
        audioFixService.cancelAllJobs(true);
        cloudflaredManager.removeListener(this);
    }

    public boolean isFfmpegAvailable() {
        return ffmpegManager.isAvailable();
    }

    public void recheckFfmpeg() {
        audioFixService.recheckFfmpeg();
    }

    public String ffmpegInstallHint() {
        return audioFixService.ffmpegInstallHint();
    }

    public void clearAudioCache() throws IOException {
        audioFixService.clearCache();
    }

    public double audioCacheSizeGb() throws IOException {
        return audioFixService.cacheSizeBytes() / (1024.0 * 1024.0 * 1024.0);
    }

    public void notifyViewersChanged() {
        List<ViewerView> viewers = activeViewers();
        for (SharingUiListener l : listeners) {
            l.onViewersChanged(viewers);
        }
    }

    private void notifyServerReady(int port) {
        for (SharingUiListener l : listeners) {
            l.onServerReady(port);
        }
    }

    private void notifySharingChanged(boolean active) {
        for (SharingUiListener l : listeners) {
            l.onSharingChanged(active);
        }
    }

    private String describeShare(ShareEntry entry) {
        Path path = Path.of(entry.getPath());
        try {
            if (entry.getKind() == ShareEntry.Kind.FILE) {
                long size = Files.size(path);
                if (ShareRegistry.isVideoFileName(path.getFileName().toString())) {
                    return "1 file · 1 video · " + humanSize(size);
                }
                return "1 file · " + humanSize(size);
            }
            Path root = path.toAbsolutePath().normalize();
            int fileCount = 0;
            int videoCount = 0;
            long totalSize = 0;
            try (var walk = Files.walk(root)) {
                for (Path f : walk.filter(Files::isRegularFile).toList()) {
                    if (!ShareExposeFilter.shouldExposeFromFolder(root, f)) {
                        continue;
                    }
                    fileCount++;
                    totalSize += Files.size(f);
                    if (ShareRegistry.isVideoFileName(f.getFileName().toString())) {
                        videoCount++;
                    }
                }
            }
            if (fileCount == 0) {
                return "0 files · 0 B";
            }
            StringBuilder meta = new StringBuilder();
            meta.append(fileCount).append(fileCount == 1 ? " file" : " files");
            if (videoCount > 0) {
                meta.append(" · ").append(videoCount).append(videoCount == 1 ? " video" : " videos");
            }
            meta.append(" · ").append(humanSize(totalSize));
            return meta.toString();
        } catch (IOException ex) {
            return "Unavailable";
        }
    }

    private int countVideosInShare(ShareEntry entry) {
        Path path = Path.of(entry.getPath());
        try {
            if (entry.getKind() == ShareEntry.Kind.FILE) {
                return ShareRegistry.isVideoFileName(path.getFileName().toString()) ? 1 : 0;
            }
            Path root = path.toAbsolutePath().normalize();
            int videos = 0;
            try (var walk = Files.walk(root)) {
                for (Path f : walk.filter(Files::isRegularFile).toList()) {
                    if (ShareExposeFilter.shouldExposeFromFolder(root, f)
                            && ShareRegistry.isVideoFileName(f.getFileName().toString())) {
                        videos++;
                    }
                }
            }
            return videos;
        } catch (IOException ex) {
            return 0;
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int u = -1;
        while (value >= 1024 && u < units.length - 1) {
            value /= 1024;
            u++;
        }
        return String.format("%.1f %s", value, units[u]);
    }

    @Override
    public void onDownloadProgress(int percent, String message) {
        for (SharingUiListener l : listeners) {
            l.onTunnelDownloadProgress(percent, message);
        }
    }

    @Override
    public void onTunnelUrl(String url) {
        for (SharingUiListener l : listeners) {
            l.onTunnelUrl(url);
        }
    }

    @Override
    public void onStateChanged(CloudflaredManager.State state, String detail) {
        TunnelState mapped = mapTunnelState(state);
        for (SharingUiListener l : listeners) {
            l.onTunnelStateChanged(mapped, detail);
        }
    }

    private static TunnelState mapTunnelState(CloudflaredManager.State state) {
        return switch (state) {
            case DOWNLOADING -> TunnelState.DOWNLOADING;
            case STARTING -> TunnelState.STARTING;
            case CONNECTED -> TunnelState.CONNECTED;
            case ERROR -> TunnelState.ERROR;
            case IDLE -> TunnelState.IDLE;
        };
    }

    @Override
    public void onError(String message, boolean retryable) {
        onError(message, retryable, cloudflaredManager.lastFailure());
    }

    @Override
    public void onError(String message, boolean retryable, DownloadFailureDetails details) {
        for (SharingUiListener l : listeners) {
            l.onTunnelError(message, retryable, details);
        }
    }
}
