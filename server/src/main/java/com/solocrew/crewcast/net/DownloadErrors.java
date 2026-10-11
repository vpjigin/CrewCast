package com.solocrew.crewcast.net;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

public final class DownloadErrors {

    private DownloadErrors() {}

    public static String plainReason(Throwable t, String url) {
        if (t == null) {
            return "Download failed";
        }
        Throwable root = rootCause(t);
        String msg = root.getMessage();
        if (root instanceof SSLHandshakeException || isPkixMessage(msg)) {
            String issuer = extractIssuerHint(msg);
            String base =
                    "Your computer or antivirus is inspecting HTTPS traffic with its own certificate";
            if (issuer != null && !issuer.isBlank()) {
                return base + " (issuer: " + issuer + ")";
            }
            return base;
        }
        if (root instanceof UnknownHostException) {
            String host = hostFromUrl(url);
            return host != null
                    ? "Could not reach " + host + ": unknown host"
                    : "Could not reach the download server: unknown host";
        }
        if (root instanceof ConnectException) {
            String host = hostFromUrl(url);
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("timed out")) {
                return host != null
                        ? "Could not reach " + host + ": connection timed out"
                        : "Connection timed out";
            }
            return host != null ? "Could not reach " + host + ": connection refused" : "Connection failed";
        }
        if (root instanceof SocketTimeoutException || root instanceof TimeoutException) {
            return "Connection timed out while downloading";
        }
        if (msg != null && msg.toLowerCase(Locale.ROOT).contains("checksum")) {
            return "Checksum mismatch";
        }
        if (msg != null && msg.toLowerCase(Locale.ROOT).contains("zip")) {
            if (msg.toLowerCase(Locale.ROOT).contains("unpack")
                    || msg.toLowerCase(Locale.ROOT).contains("extract")
                    || msg.toLowerCase(Locale.ROOT).contains("traversal")) {
                return "Could not unpack the zip";
            }
        }
        if (msg != null && msg.startsWith("cloudflared exited with code")) {
            return msg;
        }
        if (msg != null && !msg.isBlank()) {
            return msg;
        }
        return root.getClass().getSimpleName();
    }

    public static DownloadFailureDetails details(Throwable t, String url) {
        return DownloadFailureDetails.of(plainReason(t, url), url);
    }

    public static String proxyAuthMessage() {
        return "A network proxy requires authentication — configure Windows or browser proxy credentials, "
                + "or ask your IT department";
    }

    public static boolean isProxyAuth(int status) {
        return status == HttpURLConnection.HTTP_PROXY_AUTH;
    }

    private static boolean isPkixMessage(String msg) {
        if (msg == null) {
            return false;
        }
        String m = msg.toLowerCase(Locale.ROOT);
        return m.contains("pkix path building failed")
                || m.contains("unable to find valid certification path")
                || m.contains("certificate");
    }

    private static String extractIssuerHint(String msg) {
        if (msg == null) {
            return null;
        }
        int idx = msg.toLowerCase(Locale.ROOT).indexOf("issuer");
        if (idx >= 0) {
            return msg.substring(idx).lines().findFirst().orElse(null);
        }
        return null;
    }

    private static String hostFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }

    public static IOException wrapIo(String context, Throwable t) {
        IOException io = t instanceof IOException ? (IOException) t : new IOException(t.getMessage(), t);
        if (context != null && io.getMessage() != null && !io.getMessage().contains(context)) {
            return new IOException(context + ": " + io.getMessage(), io);
        }
        return io;
    }
}
