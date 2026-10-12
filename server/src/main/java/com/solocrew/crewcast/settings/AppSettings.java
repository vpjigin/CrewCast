package com.solocrew.crewcast.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppSettings {

    public enum TunnelProtocol {
        AUTOMATIC, QUIC, HTTP2
    }

    private List<ShareEntry> shares = new ArrayList<>();
    private boolean requirePrivateKey = true;
    private boolean allowSameWifiAccess = true;
    private TunnelProtocol tunnelProtocol = TunnelProtocol.AUTOMATIC;
    private boolean startTunnelWhenSharingStarts = true;
    private boolean keepComputerAwake = true;
    private String preferredAudioLanguage = "en";
    private DefaultCaptionsMode defaultCaptions = DefaultCaptionsMode.OFF;
    private int thumbnailCount = 20;
    private boolean openAtLogin = false;
    private boolean fixAudioForBrowsers = false;
    private int audioCacheMaxGb = 20;
    private PrepareAudioMode prepareAudioInAdvance = PrepareAudioMode.OFF;
    private boolean chapterThumbnails = true;
    /** Optional user override; not downloaded automatically when set. */
    private String cloudflaredBinaryPath;
    /** Optional path to ffmpeg binary (ffprobe must sit beside it). */
    private String ffmpegBinaryPath;
    private FfmpegDownloadConsent ffmpegDownloadConsent = FfmpegDownloadConsent.UNSET;
    /** Max height for TV compatibility transcodes (720 or 1080). */
    private int compatMaxHeight = 1080;
    private PrepareCompatMode prepareCompatInAdvance = PrepareCompatMode.OFF;
    private boolean allowViewerCompatPrepare = true;

    public List<ShareEntry> getShares() {
        return shares;
    }

    public void setShares(List<ShareEntry> shares) {
        this.shares = shares != null ? shares : new ArrayList<>();
    }

    public boolean isRequirePrivateKey() {
        return requirePrivateKey;
    }

    public void setRequirePrivateKey(boolean requirePrivateKey) {
        this.requirePrivateKey = requirePrivateKey;
    }

    public boolean isAllowSameWifiAccess() {
        return allowSameWifiAccess;
    }

    public void setAllowSameWifiAccess(boolean allowSameWifiAccess) {
        this.allowSameWifiAccess = allowSameWifiAccess;
    }

    public TunnelProtocol getTunnelProtocol() {
        return tunnelProtocol;
    }

    public void setTunnelProtocol(TunnelProtocol tunnelProtocol) {
        this.tunnelProtocol = tunnelProtocol != null ? tunnelProtocol : TunnelProtocol.AUTOMATIC;
    }

    public boolean isStartTunnelWhenSharingStarts() {
        return startTunnelWhenSharingStarts;
    }

    public void setStartTunnelWhenSharingStarts(boolean startTunnelWhenSharingStarts) {
        this.startTunnelWhenSharingStarts = startTunnelWhenSharingStarts;
    }

    public boolean isKeepComputerAwake() {
        return keepComputerAwake;
    }

    public void setKeepComputerAwake(boolean keepComputerAwake) {
        this.keepComputerAwake = keepComputerAwake;
    }

    public String getPreferredAudioLanguage() {
        return preferredAudioLanguage;
    }

    public void setPreferredAudioLanguage(String preferredAudioLanguage) {
        this.preferredAudioLanguage = preferredAudioLanguage;
    }

    public DefaultCaptionsMode getDefaultCaptions() {
        return defaultCaptions != null ? defaultCaptions : DefaultCaptionsMode.OFF;
    }

    public void setDefaultCaptions(DefaultCaptionsMode defaultCaptions) {
        this.defaultCaptions = defaultCaptions != null ? defaultCaptions : DefaultCaptionsMode.OFF;
    }

    public int getThumbnailCount() {
        return thumbnailCount;
    }

    public void setThumbnailCount(int thumbnailCount) {
        this.thumbnailCount = thumbnailCount;
    }

    public boolean isOpenAtLogin() {
        return openAtLogin;
    }

    public void setOpenAtLogin(boolean openAtLogin) {
        this.openAtLogin = openAtLogin;
    }

    public boolean isFixAudioForBrowsers() {
        return fixAudioForBrowsers;
    }

    public void setFixAudioForBrowsers(boolean fixAudioForBrowsers) {
        this.fixAudioForBrowsers = fixAudioForBrowsers;
    }

    public int getAudioCacheMaxGb() {
        return audioCacheMaxGb;
    }

    public void setAudioCacheMaxGb(int audioCacheMaxGb) {
        this.audioCacheMaxGb = Math.max(1, audioCacheMaxGb);
    }

    public int getCompatMaxHeight() {
        return compatMaxHeight >= 720 && compatMaxHeight < 1080 ? 720 : 1080;
    }

    public void setCompatMaxHeight(int compatMaxHeight) {
        this.compatMaxHeight = compatMaxHeight <= 720 ? 720 : 1080;
    }

    public PrepareCompatMode getPrepareCompatInAdvance() {
        return prepareCompatInAdvance != null ? prepareCompatInAdvance : PrepareCompatMode.OFF;
    }

    public void setPrepareCompatInAdvance(PrepareCompatMode prepareCompatInAdvance) {
        this.prepareCompatInAdvance = prepareCompatInAdvance != null ? prepareCompatInAdvance : PrepareCompatMode.OFF;
    }

    public boolean isAllowViewerCompatPrepare() {
        return allowViewerCompatPrepare;
    }

    public void setAllowViewerCompatPrepare(boolean allowViewerCompatPrepare) {
        this.allowViewerCompatPrepare = allowViewerCompatPrepare;
    }

    public PrepareAudioMode getPrepareAudioInAdvance() {
        return prepareAudioInAdvance != null ? prepareAudioInAdvance : PrepareAudioMode.OFF;
    }

    public void setPrepareAudioInAdvance(PrepareAudioMode prepareAudioInAdvance) {
        this.prepareAudioInAdvance = prepareAudioInAdvance != null ? prepareAudioInAdvance : PrepareAudioMode.OFF;
    }

    /** Legacy settings.json: true → ALL_TRACKS, false → OFF. */
    @JsonProperty("prepareAllAudioTracksInAdvance")
    public void setLegacyPrepareAllAudioTracksInAdvance(Boolean legacy) {
        if (legacy != null) {
            prepareAudioInAdvance = legacy ? PrepareAudioMode.ALL_TRACKS : PrepareAudioMode.OFF;
        }
    }

    public boolean isChapterThumbnails() {
        return chapterThumbnails;
    }

    public void setChapterThumbnails(boolean chapterThumbnails) {
        this.chapterThumbnails = chapterThumbnails;
    }

    public String getCloudflaredBinaryPath() {
        return cloudflaredBinaryPath;
    }

    public void setCloudflaredBinaryPath(String cloudflaredBinaryPath) {
        this.cloudflaredBinaryPath = blankToNull(cloudflaredBinaryPath);
    }

    public String getFfmpegBinaryPath() {
        return ffmpegBinaryPath;
    }

    public void setFfmpegBinaryPath(String ffmpegBinaryPath) {
        this.ffmpegBinaryPath = blankToNull(ffmpegBinaryPath);
    }

    public FfmpegDownloadConsent getFfmpegDownloadConsent() {
        return ffmpegDownloadConsent != null ? ffmpegDownloadConsent : FfmpegDownloadConsent.UNSET;
    }

    public void setFfmpegDownloadConsent(FfmpegDownloadConsent ffmpegDownloadConsent) {
        this.ffmpegDownloadConsent = ffmpegDownloadConsent != null ? ffmpegDownloadConsent : FfmpegDownloadConsent.UNSET;
    }

    /** Legacy settings.json — session keys must never be persisted; dropped on load. */
    @JsonProperty("accessToken")
    @JsonIgnore
    public void setLegacyAccessToken(String ignored) {
    }

    @JsonProperty("accessKey")
    @JsonIgnore
    public void setLegacyAccessKey(String ignored) {
    }

    @JsonProperty("sessionToken")
    @JsonIgnore
    public void setLegacySessionToken(String ignored) {
    }

    @JsonProperty("sharingToken")
    @JsonIgnore
    public void setLegacySharingToken(String ignored) {
    }

    @JsonProperty("token")
    @JsonIgnore
    public void setLegacyToken(String ignored) {
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
