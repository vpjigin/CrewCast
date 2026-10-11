package com.solocrew.crewcast.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.system.CrewcastPaths;
import com.solocrew.crewcast.system.CrewcastWriteGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
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

    public CloudflaredManager(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
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
        try {
            Path binary = ensureBinary();
            log.info("cloudflared: using binary {}", binary.toAbsolutePath());
            launchProcess(binary);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Tunnel failed";
            setState(State.ERROR, msg);
            notifyError(msg, true);
        }
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
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        Path tmp = dest.resolveSibling(dest.getFileName() + ".download");
        Files.deleteIfExists(tmp);
        Files.deleteIfExists(dest);
        long bytesDownloaded;
        try {
            bytesDownloaded = downloadToFile(client, asset.downloadUrl(), tmp, asset.size());
            log.info("cloudflared: downloaded {} bytes to {}", bytesDownloaded, tmp);
            verifySha256(asset.expectedSha256(), tmp);
            log.info("cloudflared: SHA-256 verified for {}", asset.name());
            if (asset.name().endsWith(".tgz")) {
                extractTgz(tmp, dest);
                Files.deleteIfExists(tmp);
                log.info("cloudflared: extracted archive to {} ({} bytes)", dest, Files.size(dest));
            } else {
                Files.move(tmp, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                log.info("cloudflared: installed to {} ({} bytes)", dest, Files.size(dest));
            }
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            Files.deleteIfExists(dest);
            throw e instanceof IOException ? (IOException) e : new IOException(e.getMessage(), e);
        }
        notifyDownload(100, "Download complete");
    }

    private long downloadToFile(HttpClient client, String downloadUrl, Path tmp, long expectedSize)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IOException("Download failed: HTTP " + response.statusCode());
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(expectedSize);
        long done = 0;
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) >= 0) {
                out.write(buf, 0, read);
                done += read;
                if (total > 0) {
                    int pct = (int) Math.min(100, (done * 100) / total);
                    notifyDownload(pct, "Downloading cloudflared… " + pct + "%");
                }
            }
        }
        return done;
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
            throw new IOException("cloudflared download failed SHA-256 verification");
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
        try {
            Process p = new ProcessBuilder("tar", "-xzf", tgz.toString(), "-C", CrewcastPaths.binDir().toString())
                    .redirectErrorStream(true)
                    .start();
            if (p.waitFor() != 0) {
                throw new IOException("Failed to extract cloudflared archive");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while extracting cloudflared", e);
        }
        Path extracted = CrewcastPaths.binDir().resolve("cloudflared");
        if (Files.exists(extracted)) {
            Files.move(extracted, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else if (!Files.exists(dest)) {
            throw new IOException("cloudflared binary not found after extract");
        }
    }

    private JsonNode fetchLatestRelease() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_LATEST_RELEASE))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Crewcast")
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("GitHub API error: " + resp.statusCode());
        }
        return mapper.readTree(resp.body());
    }

    private void launchProcess(Path binary) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(binary.toAbsolutePath().toString());
        cmd.add("tunnel");
        cmd.add("--no-autoupdate");
        cmd.add("--url");
        cmd.add("http://localhost:" + localPort);
        if (protocol == AppSettings.TunnelProtocol.QUIC) {
            cmd.add("--protocol");
            cmd.add("quic");
        } else if (protocol == AppSettings.TunnelProtocol.HTTP2) {
            cmd.add("--protocol");
            cmd.add("http2");
        }
        log.info("cloudflared: launching {}", String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        process = pb.start();
        setState(State.STARTING, "Waiting for public URL…");

        readerThread = new Thread(() -> readOutput(process), "cloudflared-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        Thread watchdog = new Thread(() -> {
            try {
                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                if (publicUrl == null && finished) {
                    setState(State.ERROR, "cloudflared exited before URL was ready");
                    notifyError("Tunnel process exited before a URL appeared", true);
                } else if (publicUrl == null) {
                    setState(State.ERROR, "No public URL after 30 seconds");
                    notifyError("No public URL after 30 seconds", true);
                    stopInternal(false);
                }
            } catch (InterruptedException ignored) {
            }
        }, "cloudflared-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void readOutput(Process proc) {
        try (var reader = proc.inputReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = TUNNEL_URL.matcher(line);
                if (m.find() && publicUrl == null) {
                    publicUrl = m.group().replaceAll("/$", "");
                    log.info("cloudflared: public URL {}", publicUrl);
                    setState(State.CONNECTED, publicUrl);
                    for (CloudflaredListener l : listeners) {
                        l.onTunnelUrl(publicUrl);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        if (publicUrl == null && proc == process) {
            setState(State.ERROR, "Tunnel process ended");
            notifyError("Tunnel process ended unexpectedly", true);
        }
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

    private void notifyError(String msg, boolean retryable) {
        String full = msg;
        if (msg != null && (msg.contains("SHA-256") || msg.contains("digest") || msg.contains("Download failed"))) {
            full = msg + " — " + installHint() + ", or choose a cloudflared binary in Settings.";
        }
        for (CloudflaredListener l : listeners) {
            l.onError(full, retryable);
        }
    }
}
