package com.solocrew.crewcast.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.FfmpegDownloadConsent;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.system.CrewcastPaths;
import com.solocrew.crewcast.system.CrewcastWriteGuard;
import com.solocrew.crewcast.net.DownloadErrors;
import com.solocrew.crewcast.net.DownloadFailureDetails;
import com.solocrew.crewcast.net.ResilientHttpFetcher;
import com.solocrew.crewcast.tunnel.CloudflaredPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class FfmpegDownloader {

    private static final Logger log = LoggerFactory.getLogger(FfmpegDownloader.class);

    private static final String BTBN_RELEASES_API =
            "https://api.github.com/repos/BtbN/FFmpeg-Builds/releases/latest";
    private static final Set<String> ALLOWED_URL_PREFIXES = Set.of(
            "https://github.com/BtbN/FFmpeg-Builds/releases/download/",
            "https://api.github.com/repos/BtbN/FFmpeg-Builds/",
            "https://ffmpeg.martin-riedl.de/download/",
            "https://ffmpeg.martin-riedl.de/redirect/");

    private static final Pattern SHA256_LINE = Pattern.compile("^([a-fA-F0-9]{64})\\s+(.+)$");

    private final SettingsStore settingsStore;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<FfmpegDownloadListener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean downloadInProgress;
    private volatile DownloadFailureDetails lastFailure;
    private volatile String lastAttemptedUrl;
    private final ResilientHttpFetcher http = new ResilientHttpFetcher(FfmpegDownloader.class.getName());

    public FfmpegDownloader(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

    public void addListener(FfmpegDownloadListener listener) {
        listeners.add(listener);
    }

    public void removeListener(FfmpegDownloadListener listener) {
        listeners.remove(listener);
    }

    public boolean isDownloadInProgress() {
        return downloadInProgress;
    }

    public DownloadFailureDetails lastFailure() {
        return lastFailure;
    }

    public FfmpegDownloadAssets.Plan planForCurrentHost() {
        CloudflaredPlatform.Info platform = CloudflaredPlatform.detect();
        log.info(
                "ffmpeg download: host CPU os={} arch={} rawMachine={} jvmOsArch={} ({})",
                platform.os(),
                platform.arch(),
                platform.rawMachine(),
                platform.jvmOsArch(),
                platform.detail());
        FfmpegDownloadAssets.Plan plan = FfmpegDownloadAssets.planFor(platform);
        log.info("ffmpeg download: selected provider {} ({})", plan.provider(), plan.hostLabel());
        return plan;
    }

    /**
     * Download ffmpeg+ffprobe into {@code ~/.crewcast/bin} when allowed.
     *
     * @param userInitiated true when user confirmed dialog or {@code --download-ffmpeg}
     */
    public synchronized void downloadIfNeeded(boolean userInitiated) throws IOException, InterruptedException {
        if (!userInitiated && !forceDownload()) {
            FfmpegDownloadConsent consent = settingsStore.getSettings().getFfmpegDownloadConsent();
            if (consent != FfmpegDownloadConsent.ALLOWED) {
                throw new IOException("ffmpeg download not consented (settings: " + consent + ")");
            }
        }
        if (localBinariesReady()) {
            log.info("ffmpeg download: reusing cached binaries in {}", CrewcastPaths.binDir());
            return;
        }
        downloadInProgress = true;
        lastFailure = null;
        try {
            FfmpegDownloadAssets.Plan plan = planForCurrentHost();
            Path binDir = CrewcastPaths.binDir();
            CrewcastWriteGuard.check(binDir);
            Files.createDirectories(binDir);
            Path ffmpegDest = ffmpegLocalPath();
            Path ffprobeDest = ffprobeLocalPath();
            Files.deleteIfExists(ffmpegDest);
            Files.deleteIfExists(ffprobeDest);

            if (plan.provider() == FfmpegDownloadAssets.Provider.BTBN_GITHUB) {
                downloadBtbN(plan, binDir);
            } else {
                downloadMartinRiedlMac(plan, binDir);
            }
            verifyCli(ffmpegDest);
            verifyCli(ffprobeDest);
            log.info("ffmpeg download: installed {} and {}", ffmpegDest, ffprobeDest);
            notifyFinished(true, "ffmpeg ready");
        } catch (Exception e) {
            log.error("ffmpeg download failed", e);
            lastFailure = DownloadErrors.details(e, lastAttemptedUrl != null ? lastAttemptedUrl : "");
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            if (e instanceof InterruptedException) {
                throw (InterruptedException) e;
            }
            throw new IOException(DownloadErrors.plainReason(e, lastAttemptedUrl), e);
        } finally {
            downloadInProgress = false;
        }
    }

    public boolean localBinariesReady() {
        return Files.isRegularFile(ffmpegLocalPath())
                && Files.isRegularFile(ffprobeLocalPath())
                && FfmpegManager.commandWorks(ffmpegLocalPath())
                && FfmpegManager.commandWorks(ffprobeLocalPath());
    }

    public static Path ffmpegLocalPath() {
        return binaryPath("ffmpeg");
    }

    public static Path ffprobeLocalPath() {
        return binaryPath("ffprobe");
    }

    private static Path binaryPath(String name) {
        String file = isWindows() ? name + ".exe" : name;
        return CrewcastPaths.binDir().resolve(file);
    }

    private void downloadBtbN(FfmpegDownloadAssets.Plan plan, Path binDir) throws IOException, InterruptedException {
        JsonNode release = fetchJson(BTBN_RELEASES_API);
        String tag = release.get("tag_name").asText();
        String assetName = plan.btbNArchiveName();
        String downloadUrl = null;
        String expectedSha = null;
        long size = 0;
        for (JsonNode asset : release.get("assets")) {
            if (assetName.equals(asset.get("name").asText())) {
                downloadUrl = asset.get("browser_download_url").asText();
                size = asset.get("size").asLong();
                if (asset.hasNonNull("digest")) {
                    expectedSha = parseDigestSha256(asset.get("digest").asText());
                }
                break;
            }
        }
        if (downloadUrl == null) {
            throw new IOException("No BtbN asset " + assetName + " in release " + tag);
        }
        if (expectedSha == null || expectedSha.isBlank()) {
            expectedSha = sha256FromChecksumsFile(release, assetName);
        }
        if (expectedSha == null || expectedSha.isBlank()) {
            throw new IOException(
                    "No SHA-256 checksum published for " + assetName + ". Install ffmpeg manually.");
        }
        log.info("ffmpeg download: BtbN release {} asset {} size {} sha256={}", tag, assetName, size, expectedSha);
        assertAllowedUrl(downloadUrl);
        Path archive = binDir.resolve(assetName);
        try {
            long bytes = downloadFile(downloadUrl, archive, size, "Downloading ffmpeg…");
            log.info("ffmpeg download: downloaded {} bytes", bytes);
            verifySha256(expectedSha, archive);
            log.info("ffmpeg download: SHA-256 verified for {}", assetName);
            Path extractDir = Files.createTempDirectory(binDir, "ffmpeg-extract-");
            try {
                if (assetName.endsWith(".zip")) {
                    unzip(archive, extractDir);
                } else if (assetName.endsWith(".tar.xz")) {
                    extractTarXz(archive, extractDir);
                } else {
                    throw new IOException("Unsupported archive: " + assetName);
                }
                Path ffmpeg = findBinary(extractDir, "ffmpeg");
                Path ffprobe = findBinary(extractDir, "ffprobe");
                installBinary(ffmpeg, ffmpegLocalPath());
                installBinary(ffprobe, ffprobeLocalPath());
            } finally {
                deleteRecursive(extractDir);
            }
        } catch (Exception e) {
            ResilientHttpFetcher.deleteQuietly(archive);
            ResilientHttpFetcher.deleteQuietly(ResilientHttpFetcher.partPath(archive));
            throw e;
        } finally {
            ResilientHttpFetcher.deleteQuietly(archive);
        }
    }

    private void downloadMartinRiedlMac(FfmpegDownloadAssets.Plan plan, Path binDir)
            throws IOException, InterruptedException {
        String cpu = plan.macOsCpu();
        downloadMartinZip(cpu, "ffmpeg", binDir, ffmpegLocalPath());
        downloadMartinZip(cpu, "ffprobe", binDir, ffprobeLocalPath());
    }

    private void downloadMartinZip(String cpu, String binary, Path binDir, Path dest)
            throws IOException, InterruptedException {
        String redirectStart = "https://ffmpeg.martin-riedl.de/redirect/latest/macos/"
                + cpu
                + "/snapshot/"
                + binary
                + ".zip";
        assertAllowedUrl(redirectStart);
        URI resolved = resolveRedirect(redirectStart);
        String downloadUrl = resolved.toString();
        assertAllowedUrl(downloadUrl);
        String shaUrl = downloadUrl + ".sha256";
        assertAllowedUrl(shaUrl);
        String expectedSha = fetchSha256Sidecar(shaUrl, binary + ".zip");
        log.info("ffmpeg download: macOS {} from {} sha256={}", binary, downloadUrl, expectedSha);
        Path zipFile = binDir.resolve(binary + ".zip");
        try {
            long bytes = downloadFile(downloadUrl, zipFile, 0, "Downloading " + binary + "…");
            log.info("ffmpeg download: downloaded {} bytes for {}", bytes, binary);
            verifySha256(expectedSha, zipFile);
            log.info("ffmpeg download: SHA-256 verified for {}", binary);
            Path extractDir = Files.createTempDirectory(binDir, "ffmpeg-extract-");
            try {
                unzipSingleBinary(zipFile, extractDir, binary);
                Path found = findBinary(extractDir, binary);
                installBinary(found, dest);
            } finally {
                deleteRecursive(extractDir);
            }
        } catch (Exception e) {
            ResilientHttpFetcher.deleteQuietly(zipFile);
            ResilientHttpFetcher.deleteQuietly(ResilientHttpFetcher.partPath(zipFile));
            throw e;
        } finally {
            ResilientHttpFetcher.deleteQuietly(zipFile);
        }
    }

    static String parseDigestSha256(String digest) {
        if (digest == null || digest.isBlank()) {
            return null;
        }
        String d = digest.trim().toLowerCase(Locale.ROOT);
        if (d.startsWith("sha256:")) {
            return d.substring("sha256:".length());
        }
        return d.length() == 64 ? d : null;
    }

    static void assertAllowedUrl(String url) throws IOException {
        if (url == null || !url.startsWith("https://")) {
            throw new IOException("ffmpeg download requires HTTPS");
        }
        boolean ok = false;
        for (String prefix : ALLOWED_URL_PREFIXES) {
            if (url.startsWith(prefix)) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            throw new IOException("ffmpeg download URL not allow-listed: " + url);
        }
    }

    static String parseSha256Sidecar(String body, String expectedFilename) throws IOException {
        for (String line : body.split("\n")) {
            Matcher m = SHA256_LINE.matcher(line.trim());
            if (m.matches()) {
                String name = m.group(2).trim();
                if (name.equals(expectedFilename) || expectedFilename == null) {
                    return m.group(1).toLowerCase(Locale.ROOT);
                }
            }
        }
        throw new IOException("SHA-256 sidecar missing hash for " + expectedFilename);
    }

    private String sha256FromChecksumsFile(JsonNode release, String assetName)
            throws IOException, InterruptedException {
        for (JsonNode asset : release.get("assets")) {
            if ("checksums.sha256".equals(asset.get("name").asText())) {
                String url = asset.get("browser_download_url").asText();
                assertAllowedUrl(url);
                HttpResponse<String> resp = http.getString(url);
                if (resp.statusCode() >= 400) {
                    throw new IOException("Failed to fetch checksums.sha256: HTTP " + resp.statusCode());
                }
                for (String line : resp.body().split("\n")) {
                    Matcher m = SHA256_LINE.matcher(line.trim());
                    if (m.matches() && assetName.equals(m.group(2).trim())) {
                        return m.group(1).toLowerCase(Locale.ROOT);
                    }
                }
            }
        }
        return null;
    }

    private String fetchSha256Sidecar(String shaUrl, String filename) throws IOException, InterruptedException {
        HttpResponse<String> resp = http.getString(shaUrl);
        if (resp.statusCode() >= 400) {
            throw new IOException("Failed to fetch checksum: HTTP " + resp.statusCode());
        }
        return parseSha256Sidecar(resp.body(), filename);
    }

    private JsonNode fetchJson(String url) throws IOException, InterruptedException {
        assertAllowedUrl(url);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        HttpResponse<String> resp = http.client().send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("GitHub API failed: HTTP " + resp.statusCode());
        }
        return mapper.readTree(resp.body());
    }

    private URI resolveRedirect(String url) throws IOException, InterruptedException {
        return http.resolveRedirect(url);
    }

    private long downloadFile(String url, Path dest, long expectedSize, String label)
            throws IOException, InterruptedException {
        assertAllowedUrl(url);
        lastAttemptedUrl = url;
        return http.downloadToFile(
                url,
                dest,
                expectedSize,
                pct -> {
                    if (pct < 100) {
                        notifyProgress(pct, label + " " + pct + "%");
                    } else {
                        notifyProgress(100, label);
                    }
                });
    }

    private static void verifySha256(String expectedHex, Path file) throws IOException {
        String actual = sha256Hex(file);
        if (!expectedHex.equalsIgnoreCase(actual)) {
            throw new IOException("Checksum mismatch (expected " + expectedHex + ", got " + actual + ")");
        }
    }

    static String sha256Hex(Path file) throws IOException {
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
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static void extractTarXz(Path archive, Path destDir) throws IOException, InterruptedException {
        Files.createDirectories(destDir);
        Process p = new ProcessBuilder("tar", "-xJf", archive.toAbsolutePath().toString(), "-C", destDir.toString())
                .redirectErrorStream(true)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("tar extract timed out");
        }
        if (p.exitValue() != 0) {
            throw new IOException("tar extract failed: " + out);
        }
    }

    private static void unzip(Path zip, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(destDir.resolve(entry.getName()));
                    continue;
                }
                Path out = destDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(destDir)) {
                    throw new IOException("Could not unpack the zip: path traversal blocked");
                }
                Files.createDirectories(out.getParent());
                Files.copy(zis, out);
            }
        } catch (IOException e) {
            throw new IOException("Could not unpack the zip", e);
        }
    }

    private static void unzipSingleBinary(Path zip, Path destDir, String binaryName) throws IOException {
        unzip(zip, destDir);
    }

    private static Path findBinary(Path root, String baseName) throws IOException {
        List<Path> matches = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                if (name.equals(baseName) || name.equals(baseName + ".exe")) {
                    matches.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        if (matches.isEmpty()) {
            throw new IOException("Could not find " + baseName + " in extracted archive");
        }
        return matches.getFirst();
    }

    private static void installBinary(Path from, Path to) throws IOException {
        CrewcastWriteGuard.check(to);
        Files.createDirectories(to.getParent());
        Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        makeExecutable(to);
    }

    private static void makeExecutable(Path path) throws IOException {
        if (isWindows()) {
            return;
        }
        try {
            Files.setPosixFilePermissions(
                    path,
                    EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static void verifyCli(Path binary) throws IOException, InterruptedException {
        if (!FfmpegManager.commandWorks(binary)) {
            throw new IOException("Downloaded binary failed ffmpeg -version: " + binary);
        }
        Process p = new ProcessBuilder(binary.toString(), "-version")
                .redirectErrorStream(true)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        log.info("ffmpeg download: {} -version:\n{}", binary.getFileName(), out.lines().limit(3).reduce((a, b) -> a + "\n" + b).orElse(""));
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean forceDownload() {
        return Boolean.parseBoolean(System.getProperty("crewcast.ffmpeg.forceDownload", "false"));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private void notifyProgress(int pct, String msg) {
        for (FfmpegDownloadListener l : listeners) {
            l.onFfmpegDownloadProgress(pct, msg);
        }
    }

    private void notifyFinished(boolean ok, String msg) {
        for (FfmpegDownloadListener l : listeners) {
            l.onFfmpegDownloadFinished(ok, msg);
        }
    }
}
