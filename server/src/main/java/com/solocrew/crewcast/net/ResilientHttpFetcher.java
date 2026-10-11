package com.solocrew.crewcast.net;

import com.solocrew.crewcast.system.CrewcastLogging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.function.IntConsumer;

/**
 * HTTP downloads with connect/read timeouts, retries, .part files, and structured logging.
 */
public class ResilientHttpFetcher {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    public static final Duration READ_IDLE_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 3;

    private final Logger log;
    private final HttpClient client;

    public ResilientHttpFetcher(String loggerName) {
        this.log = LoggerFactory.getLogger(loggerName);
        this.client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(ProxySelector.getDefault())
                .build();
    }

    public HttpClient client() {
        return client;
    }

    public HttpResponse<String> getString(String url) throws IOException, InterruptedException {
        return sendWithRetry(url, HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<InputStream> getStream(String url) throws IOException, InterruptedException {
        return sendWithRetry(url, HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    public URI resolveRedirect(String url) throws IOException, InterruptedException {
        log.info("HTTP HEAD resolve redirect: {}", CrewcastLogging.sanitize(url));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<Void> resp = sendWithRetry(url, req, HttpResponse.BodyHandlers.discarding());
        URI finalUri = resp.uri();
        log.info("HTTP redirect resolved: status={} finalUri={}", resp.statusCode(), finalUri);
        return finalUri;
    }

    public long downloadToFile(String url, Path destination, long expectedSizeHint, IntConsumer progressPercent)
            throws IOException, InterruptedException {
        Path part = partPath(destination);
        Files.createDirectories(destination.getParent());
        deleteQuietly(part);
        deleteQuietly(destination);
        long totalBytes = 0;
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                totalBytes = downloadOnce(url, part, expectedSizeHint, progressPercent, attempt);
                movePartToFinal(part, destination);
                return totalBytes;
            } catch (Exception e) {
                last = e;
                deleteQuietly(part);
                if (!shouldRetry(e, attempt)) {
                    break;
                }
                long backoffMs = attempt * 2000L;
                log.warn(
                        "Download attempt {}/{} failed for {}: {} — retry in {}ms",
                        attempt,
                        MAX_ATTEMPTS,
                        url,
                        e.getMessage(),
                        backoffMs,
                        e);
                Thread.sleep(backoffMs);
            }
        }
        throw last instanceof IOException
                ? (IOException) last
                : new IOException(DownloadErrors.plainReason(last, url), last);
    }

    private long downloadOnce(
            String url, Path part, long expectedSizeHint, IntConsumer progressPercent, int attempt)
            throws IOException, InterruptedException {
        log.info("HTTP GET attempt {} url={}", attempt, CrewcastLogging.sanitize(url));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<InputStream> response = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        log.info("HTTP response status={} uri={}", status, response.uri());
        if (DownloadErrors.isProxyAuth(status)) {
            throw new IOException(DownloadErrors.proxyAuthMessage());
        }
        if (status >= 500 && status < 600) {
            throw new IOException("Download server error: HTTP " + status);
        }
        if (status >= 400) {
            throw new IOException("Download failed: HTTP " + status);
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(expectedSizeHint);
        long done = 0;
        try (InputStream raw = response.body();
                InputStream in = new IdleTimeoutInputStream(raw, READ_IDLE_TIMEOUT);
                OutputStream out = Files.newOutputStream(part)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) >= 0) {
                out.write(buf, 0, read);
                done += read;
                if (progressPercent != null && total > 0) {
                    int pct = (int) Math.min(100, (done * 100) / total);
                    progressPercent.accept(pct);
                }
            }
        }
        log.info("HTTP download complete bytes={} part={}", done, part.getFileName());
        if (progressPercent != null) {
            progressPercent.accept(100);
        }
        return done;
    }

    private <T> HttpResponse<T> sendWithRetry(String url, HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<T> resp = client.send(request, handler);
                if (DownloadErrors.isProxyAuth(resp.statusCode())) {
                    throw new IOException(DownloadErrors.proxyAuthMessage());
                }
                if (resp.statusCode() >= 500 && resp.statusCode() < 600 && attempt < MAX_ATTEMPTS) {
                    throw new IOException("HTTP " + resp.statusCode());
                }
                return resp;
            } catch (Exception e) {
                last = e;
                if (!shouldRetry(e, attempt)) {
                    break;
                }
                Thread.sleep(attempt * 2000L);
            }
        }
        throw last instanceof IOException
                ? (IOException) last
                : new IOException(DownloadErrors.plainReason(last, url), last);
    }

    static boolean shouldRetry(Exception e, int attempt) {
        if (attempt >= MAX_ATTEMPTS) {
            return false;
        }
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String msg = root.getMessage() != null ? root.getMessage().toLowerCase() : "";
        if (root instanceof java.net.SocketTimeoutException
                || root instanceof java.util.concurrent.TimeoutException
                || msg.contains("timed out")) {
            return true;
        }
        if (msg.contains("http 5")) {
            return true;
        }
        return e instanceof IOException && msg.contains("server error");
    }

    public static Path partPath(Path destination) {
        String name = destination.getFileName().toString();
        return destination.resolveSibling(name + ".part");
    }

    public static void movePartToFinal(Path part, Path destination) throws IOException {
        try {
            Files.move(part, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(part, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    static final class IdleTimeoutInputStream extends InputStream {
        private final InputStream delegate;
        private final Duration idleTimeout;
        private long lastReadMs = System.currentTimeMillis();

        IdleTimeoutInputStream(InputStream delegate, Duration idleTimeout) {
            this.delegate = delegate;
            this.idleTimeout = idleTimeout;
        }

        @Override
        public int read() throws IOException {
            checkIdle();
            int r = delegate.read();
            lastReadMs = System.currentTimeMillis();
            return r;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            checkIdle();
            int r = delegate.read(b, off, len);
            lastReadMs = System.currentTimeMillis();
            return r;
        }

        private void checkIdle() throws IOException {
            if (System.currentTimeMillis() - lastReadMs > idleTimeout.toMillis()) {
                throw new IOException("Read timed out after " + idleTimeout.toSeconds() + "s without data");
            }
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
