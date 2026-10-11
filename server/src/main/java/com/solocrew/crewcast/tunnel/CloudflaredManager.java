package com.solocrew.crewcast.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.net.DownloadErrors;
import com.solocrew.crewcast.net.DownloadFailureDetails;
import com.solocrew.crewcast.net.ResilientHttpFetcher;
import com.solocrew.crewcast.archive.SafeArchive;
import com.solocrew.crewcast.system.CrewcastPaths;
import com.solocrew.crewcast.system.CrewcastWriteGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CloudflaredManager {

    private static final Logger log = LoggerFactory.getLogger(CloudflaredManager.class);

    public enum State {
        IDLE, DOWNLOADING, STARTING, CONNECTED, ERROR
    }

    private static final Pattern TUNNEL_URL =
            Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com/?", Pattern.CASE_INSENSITIVE);

    private static final String GITHUB_LATEST_RELEASE =
            "https://api.github.com/repos/cloudflare/cloudflared/releases/latest";

    private final SettingsStore settingsStore;
    private final List<CloudflaredListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile Process process;
    private volatile Thread readerThread;
    private volatile int localPort;
    private volatile AppSettings.TunnelProtocol protocol = AppSettings.TunnelProtocol.AUTOMATIC;
    private volatile String publicUrl;
    private volatile DownloadFailureDetails lastFailure;
    private volatile String lastDownloadUrl;
    private volatile boolean http2RetryAttempted;
    private volatile String connectedProtocol;

    private final ResilientHttpFetcher http = new ResilientHttpFetcher(CloudflaredManager.class.getName());

    public CloudflaredManager(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

    public DownloadFailureDetails lastFailure() {
        return lastFailure;
    }

    public String connectedProtocol() {
        return connectedProtocol;
    }

    public void addListener(CloudflaredListener listener) {
        listeners.add(listener);
    }

    public void removeListener(CloudflaredListener listener) {
        listeners.remove(listener);
    }

    public State getState() {
        return state.get();
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setProtocol(AppSettings.TunnelProtocol protocol) {
        this.protocol = protocol != null ? protocol : AppSettings.TunnelProtocol.AUTOMATIC;
    }

    public synchronized void start(int port) {
        stopInternal(false);
        localPort = port;
        publicUrl = null;
        setState(State.STARTING, "Starting tunnel…");
        new Thread(() -> runTunnelLifecycle(), "cloudflared").start();
    }

    public synchronized void stop() {
        stopInternal(true);
    }

    public synchronized void retry() {
        if (localPort > 0) {
            start(localPort);
        }
    }

    private void runTunnelLifecycle() {
        http2RetryAttempted = false;
        lastFailure = null;
        try {
            Path binary = ensureBinary();
            log.info("cloudflared: using binary {}", binary.toAbsolutePath());
            if (isWindows() && !Files.isRegularFile(binary)) {
                throw new IOException(windowsBlockedMessage(binary));
            }
            if (!launchWithOptionalHttp2Retry(binary)) {
                throw new IOException("No public URL after tunnel attempts");
            }
        } catch (Exception e) {
            log.error("cloudflared tunnel failed", e);
            lastFailure = DownloadErrors.details(e, lastDownloadUrl != null ? lastDownloadUrl : "");
            String msg = lastFailure.reason();
            if (isWindowsBlocked(e, binaryOrNull())) {
                msg = windowsBlockedMessage(localBinaryPath());
            }
            setState(State.ERROR, msg);
            notifyError(msg, true, lastFailure);
        }
    }

    private Path binaryOrNull() {
        try {
            return localBinaryPath();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean launchWithOptionalHttp2Retry(Path binary) throws IOException, InterruptedException {
        try {
            launchProcess(binary, protocol);
        } catch (IOException first) {
            if (isWindows()
                    && !http2RetryAttempted
                    && (protocol == AppSettings.TunnelProtocol.AUTOMATIC
                            || protocol == AppSettings.TunnelProtocol.QUIC)) {
                http2RetryAttempted = true;
                log.info(
                        "cloudflared: first attempt failed ({}), retrying with --protocol http2",
                        first.getMessage());
                stopInternal(false);
                launchProcess(binary, AppSettings.TunnelProtocol.HTTP2);
            } else {
                throw first;
            }
        }
        return publicUrl != null;
    }

    private Path ensureBinary() throws IOException, InterruptedException {
        Path configured = configuredBinaryPath();
        if (configured != null) {
            return configured;
        }
        Path local = localBinaryPath();
        if (Files.isRegularFile(local) && Files.size(local) > 0) {
            log.info("cloudflared: reusing cached binary at {} ({} bytes)", local, Files.size(local));
            makeExecutable(local);
            return local;
        }
        if (!forceDownload()) {
            Path onPath = findOnPath();
            if (onPath != null) {
                log.info("cloudflared: found on PATH at {}", onPath);
                return onPath;
            }
        } else {
            log.info("cloudflared: forceDownload=true — skipping PATH lookup");
        }
        setState(State.DOWNLOADING, "Downloading cloudflared…");
        downloadLatest(local);
        makeExecutable(local);
        return local;
    }

    private Path configuredBinaryPath() throws IOException {
        String raw = settingsStore.getSettings().getCloudflaredBinaryPath();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Path path = Path.of(raw.trim()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IOException("Configured cloudflared not found: " + path);
        }
        if (!Files.isExecutable(path) && !isWindows()) {
            throw new IOException("Configured cloudflared is not executable: " + path);
        }
        log.info("cloudflared: using configured path {}", path);
        return path;
    }

    private Path findOnPath() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        String exe = isWindows() ? "cloudflared.exe" : "cloudflared";
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(dir, exe);
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Path localBinaryPath() {
        String name = isWindows() ? "cloudflared.exe" : "cloudflared";
        return CrewcastPaths.binDir().resolve(name);
    }

    /** E2E: official GitHub download, checksum verify, and safe extract. */
    void downloadLatestReleaseForTest(Path dest) throws IOException, InterruptedException {
        downloadLatest(dest);
    }

    private void downloadLatest(Path dest) throws IOException, InterruptedException {
        Path binDir = CrewcastPaths.binDir();
        CrewcastWriteGuard.check(dest);
        CrewcastWriteGuard.check(binDir);
        Files.createDirectories(binDir);
        CloudflaredPlatform.Info platform = CloudflaredPlatform.detect();
        String assetName = CloudflaredPlatform.assetName(platform);
        log.info(
                "cloudflared: host CPU os={} arch={} rawMachine={} jvmOsArch={} ({})",
                platform.os(),
                platform.arch(),
                platform.rawMachine(),
                platform.jvmOsArch(),
                platform.detail());
        log.info("cloudflared: selected release asset {}", assetName);
        JsonNode release = fetchLatestRelease();
        String tag = release.get("tag_name").asText();
        ReleaseAsset asset = findAsset(release, assetName);
        if (asset == null) {
            throw new IOException("No release asset found for " + assetName + " in " + tag);
        }
        if (asset.expectedSha256() == null || asset.expectedSha256().isBlank()) {
            throw new IOException(
                    "GitHub release does not publish a SHA-256 digest for "
                            + assetName
                            + ". Install cloudflared manually ("
                            + installHint()
                            + ") or choose a binary in Settings.");
        }
        log.info(
                "cloudflared: release {} asset {} size {} expectedSha256={}",
                tag,
                asset.name(),
                asset.size(),
                asset.expectedSha256());
        if (!asset.downloadUrl().startsWith("https://github.com/cloudflare/cloudflared/releases/download/")) {
            throw new IOException("Unexpected download URL (expected official GitHub release asset)");
        }
        lastDownloadUrl = asset.downloadUrl();
        Path downloadTarget = asset.name().endsWith(".tgz") ? binDir.resolve(asset.name()) : dest;
        ResilientHttpFetcher.deleteQuietly(downloadTarget);
        ResilientHttpFetcher.deleteQuietly(ResilientHttpFetcher.partPath(downloadTarget));
        ResilientHttpFetcher.deleteQuietly(dest);
        try {
            long bytesDownloaded = http.downloadToFile(
                    asset.downloadUrl(),
                    downloadTarget,
                    asset.size(),
                    pct -> notifyDownload(pct, "Downloading cloudflared… " + pct + "%"));
            log.info("cloudflared: downloaded {} bytes to {}", bytesDownloaded, downloadTarget.getFileName());
            verifySha256(asset.expectedSha256(), downloadTarget);
            log.info("cloudflared: SHA-256 verified for {}", asset.name());
            if (asset.name().endsWith(".tgz")) {
                extractTgz(downloadTarget, dest);
                ResilientHttpFetcher.deleteQuietly(downloadTarget);
                log.info("cloudflared: extracted archive to {} ({} bytes)", dest, Files.size(dest));
            } else {
                log.info("cloudflared: installed to {} ({} bytes)", dest, Files.size(dest));
            }
        } catch (Exception e) {
            ResilientHttpFetcher.deleteQuietly(downloadTarget);
            ResilientHttpFetcher.deleteQuietly(ResilientHttpFetcher.partPath(downloadTarget));
            ResilientHttpFetcher.deleteQuietly(dest);
            throw e instanceof IOException ? (IOException) e : new IOException(e.getMessage(), e);
        }
        notifyDownload(100, "Download complete");
    }

    private record ReleaseAsset(String name, String downloadUrl, long size, String expectedSha256) {
    }

    private ReleaseAsset findAsset(JsonNode release, String assetName) {
        for (JsonNode asset : release.get("assets")) {
            if (assetName.equals(asset.get("name").asText())) {
                String digest = asset.hasNonNull("digest") ? asset.get("digest").asText() : null;
                String sha = parseDigestSha256(digest);
                return new ReleaseAsset(
                        assetName,
                        asset.get("browser_download_url").asText(),
                        asset.get("size").asLong(),
                        sha);
            }
        }
        return null;
    }

    private static String parseDigestSha256(String digest) {
        if (digest == null || digest.isBlank()) {
            return null;
        }
        String d = digest.trim().toLowerCase(Locale.ROOT);
        if (d.startsWith("sha256:")) {
            return d.substring("sha256:".length());
        }
        return d.length() == 64 ? d : null;
    }

    private void verifySha256(String expectedHex, Path file) throws IOException {
        String actual = sha256Hex(file);
        if (!expectedHex.equalsIgnoreCase(actual)) {
            throw new IOException("Checksum mismatch (expected " + expectedHex + ", got " + actual + ")");
        }
    }

    private static String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buf)) >= 0) {
                    digest.update(buf, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    private void extractTgz(Path tgz, Path dest) throws IOException {
        if (isWindows()) {
            throw new IOException("Unexpected .tgz on Windows");
        }
        CrewcastWriteGuard.check(dest);
        Path binDir = CrewcastPaths.binDir();
        Files.createDirectories(binDir);
        SafeArchive.extractTarGz(tgz, binDir);
        Path extracted = binDir.resolve("cloudflared");
        if (Files.exists(extracted)) {
            Files.move(extracted, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else if (!Files.exists(dest)) {
            throw new IOException("cloudflared binary not found after extract");
        }
    }

    private JsonNode fetchLatestRelease() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_LATEST_RELEASE))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Crewcast")
                .GET()
                .build();
        HttpResponse<String> resp = http.client().send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("GitHub API error: " + resp.statusCode());
        }
        return mapper.readTree(resp.body());
    }

    private void launchProcess(Path binary, AppSettings.TunnelProtocol useProtocol) throws IOException {
        publicUrl = null;
        List<String> cmd = new ArrayList<>();
        cmd.add(binary.toAbsolutePath().toString());
        cmd.add("tunnel");
        cmd.add("--no-autoupdate");
        cmd.add("--url");
        cmd.add("http://localhost:" + localPort);
        String protocolLabel = "automatic";
        if (useProtocol == AppSettings.TunnelProtocol.QUIC) {
            cmd.add("--protocol");
            cmd.add("quic");
            protocolLabel = "quic";
        } else if (useProtocol == AppSettings.TunnelProtocol.HTTP2) {
            cmd.add("--protocol");
            cmd.add("http2");
            protocolLabel = "http2";
        }
        log.info("cloudflared: launching protocol={} cmd={}", protocolLabel, String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        process = pb.start();
        setState(State.STARTING, "Waiting for public URL…");

        readerThread = new Thread(() -> readOutput(process, useProtocol), "cloudflared-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        Process proc = process;
        try {
            boolean finished = proc.waitFor(30, TimeUnit.SECONDS);
            if (publicUrl == null && finished) {
                int code = proc.exitValue();
                String msg = "cloudflared exited with code " + code;
                if (isWindows() && (code == 1 || code == -1073741515) && !Files.isRegularFile(binary)) {
                    msg = windowsBlockedMessage(binary);
                }
                throw new IOException(msg);
            }
            if (publicUrl == null) {
                stopInternal(false);
                throw new IOException("No public URL after 30 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for tunnel URL", e);
        }
    }

    private void readOutput(Process proc, AppSettings.TunnelProtocol useProtocol) {
        try (var reader = proc.inputReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("cloudflared: {}", line);
                Matcher m = TUNNEL_URL.matcher(line);
                if (m.find() && publicUrl == null) {
                    publicUrl = m.group().replaceAll("/$", "");
                    connectedProtocol = protocolLabel(useProtocol);
                    log.info("cloudflared: public URL {} (protocol {})", publicUrl, connectedProtocol);
                    setState(State.CONNECTED, publicUrl);
                    for (CloudflaredListener l : listeners) {
                        l.onTunnelUrl(publicUrl);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("cloudflared output read failed", e);
        }
    }

    private static String protocolLabel(AppSettings.TunnelProtocol useProtocol) {
        return switch (useProtocol) {
            case QUIC -> "quic";
            case HTTP2 -> "http2";
            case AUTOMATIC -> "automatic";
        };
    }

    private static String windowsBlockedMessage(Path binary) {
        return "Windows may have blocked or removed "
                + (binary != null ? binary.getFileName() : "cloudflared.exe")
                + " (Windows Defender or SmartScreen). Allow the app in Security settings, or install cloudflared manually.";
    }

    private static boolean isWindowsBlocked(Exception e, Path binary) {
        if (!isWindows()) {
            return false;
        }
        if (binary != null && !Files.isRegularFile(binary)) {
            return true;
        }
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
        return msg.contains("exited with code") || msg.contains("blocked");
    }

    private synchronized void stopInternal(boolean idle) {
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }
        publicUrl = null;
        if (idle) {
            setState(State.IDLE, "");
        }
    }

    private void makeExecutable(Path path) {
        CrewcastWriteGuard.check(path);
        if (isWindows()) {
            return;
        }
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(path, perms);
        } catch (Exception ignored) {
        }
    }

    private static boolean forceDownload() {
        return Boolean.getBoolean("crewcast.tunnel.forceDownload");
    }

    public static String installHint() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return "brew install cloudflared";
        }
        if (os.contains("win")) {
            return "winget install Cloudflare.cloudflared";
        }
        return "see https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private void setState(State s, String detail) {
        state.set(s);
        for (CloudflaredListener l : listeners) {
            l.onStateChanged(s, detail);
        }
    }

    private void notifyDownload(int pct, String msg) {
        for (CloudflaredListener l : listeners) {
            l.onDownloadProgress(pct, msg);
        }
    }

    private void notifyError(String msg, boolean retryable, DownloadFailureDetails details) {
        String full = msg;
        if (msg != null && (msg.contains("Checksum") || msg.contains("digest") || msg.contains("Download failed"))) {
            full = msg + " — " + installHint() + ", or choose a cloudflared binary in Settings.";
        }
        for (CloudflaredListener l : listeners) {
            l.onError(full, retryable, details);
        }
    }
}
