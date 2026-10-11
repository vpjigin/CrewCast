package com.solocrew.crewcast.facade;

import com.solocrew.crewcast.media.AudioFixService;
import com.solocrew.crewcast.media.FfmpegDownloadAssets;
import com.solocrew.crewcast.media.FfmpegDownloadListener;
import com.solocrew.crewcast.media.FfmpegDownloader;
import com.solocrew.crewcast.net.DownloadFailureDetails;
import com.solocrew.crewcast.net.DownloadErrors;
import com.solocrew.crewcast.system.CrewcastDiagnostics;
import com.solocrew.crewcast.system.CrewcastPaths;
import com.solocrew.crewcast.media.FfmpegManager;
import com.solocrew.crewcast.media.VideoBrowserContainers;
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
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SharingService implements CloudflaredListener, FfmpegDownloadListener {

    private final ShareRegistry shareRegistry;
    private final SettingsStore settingsStore;
    private final AccessSession accessSession;
    private final SharingState sharingState;
    private final ViewerRegistry viewerRegistry;
    private final CloudflaredManager cloudflaredManager;
    private final KeepAwakeManager keepAwakeManager;
    private final ServerPortHolder portHolder;
    private final AudioFixService audioFixService;
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
        this.ffmpegManager = ffmpegManager;
        this.ffmpegDownloader = ffmpegDownloader;
        this.subtitleService = subtitleService;
    }

    @PostConstruct
    void init() {
        cloudflaredManager.addListener(this);
        ffmpegDownloader.addListener(this);
        cloudflaredManager.setProtocol(settingsStore.getSettings().getTunnelProtocol());
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
    }

    public void addFolder(Path folder) throws IOException {
        shareRegistry.addFolder(folder);
    }

    public void addFile(Path file) throws IOException {
        shareRegistry.addFile(file);
    }

    public void removeShare(int index) {
        List<ShareEntry> before = shareRegistry.listShareEntries();
        if (index >= 0 && index < before.size()) {
            audioFixService.onShareRemoved(Path.of(before.get(index).getPath()));
        }
        shareRegistry.removeShare(index);
    }

    public void removeAllShares() {
        for (ShareEntry e : shareRegistry.listShareEntries()) {
            audioFixService.onShareRemoved(Path.of(e.getPath()));
        }
        shareRegistry.clearAllShares();
    }

    public List<ShareListItem> listShares() {
        List<ShareEntry> entries = shareRegistry.listShareEntries();
        List<ShareListItem> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ShareEntry e = entries.get(i);
            int videos = countVideosInShare(e);
            out.add(new ShareListItem(
                    i,
                    e,
                    describeShare(e),
                    videos > 0 ? audioFixService.summarizeForShare(e) : "",
                    subtitleService.summarizeForShare(e),
                    audioFixService.hevcWarningForShare(e)));
        }
        return out;
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
        notifySharingChanged(true);
        notifyViewersChanged();
    }

    public void stopSharing() {
        sharingState.setSharingActive(false);
        cloudflaredManager.stop();
        keepAwakeManager.stop();
        audioFixService.onSharingStopped();
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
        return viewerRegistry.activeViewers().stream()
                .map(v -> new ViewerView(v.key(), v.device(), v.fileName(), v.viaTunnel()))
                .toList();
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
