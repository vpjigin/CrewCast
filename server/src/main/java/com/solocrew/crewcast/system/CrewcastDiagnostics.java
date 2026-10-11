package com.solocrew.crewcast.system;

import com.solocrew.crewcast.media.FfmpegDownloader;
import com.solocrew.crewcast.net.DownloadErrors;
import com.solocrew.crewcast.tunnel.CloudflaredPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

public final class CrewcastDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(CrewcastDiagnostics.class);

    private static final String[] PROBE_URLS = {
        "https://api.github.com/",
        "https://github.com/",
        "https://github.com/cloudflare/cloudflared/releases/latest",
        "https://ffmpeg.martin-riedl.de/",
    };

    private CrewcastDiagnostics() {}

    public static String runReport() {
        StringBuilder sb = new StringBuilder();
        CloudflaredPlatform.Info platform = CloudflaredPlatform.detect();
        sb.append("Crewcast diagnostics\n");
        sb.append("OS: ").append(System.getProperty("os.name")).append("\n");
        sb.append("Arch: ").append(platform.os()).append(" / ").append(platform.arch()).append("\n");
        sb.append("Detail: ").append(platform.detail()).append("\n");
        sb.append("crewcast.home: ").append(CrewcastPaths.home()).append("\n");
        sb.append("Trust store: ").append(TrustStoreBootstrap.activeTrustStoreLabel()).append("\n");
        sb.append("java.net.useSystemProxies: ")
                .append(System.getProperty("java.net.useSystemProxies", "false"))
                .append("\n");
        appendProxy(sb);
        sb.append("ffmpeg local: ").append(pathStatus(FfmpegDownloader.ffmpegLocalPath())).append("\n");
        sb.append("ffprobe local: ").append(pathStatus(FfmpegDownloader.ffprobeLocalPath())).append("\n");
        sb.append("cloudflared local: ").append(pathStatus(localCloudflared())).append("\n");
        sb.append("Log file: ").append(CrewcastPaths.logFile()).append("\n");
        sb.append("\nReachability (HEAD, no large download):\n");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(ProxySelector.getDefault())
                .build();
        for (String url : PROBE_URLS) {
            sb.append("  ").append(url).append(" → ");
            sb.append(probeHead(client, url)).append("\n");
        }
        String report = sb.toString();
        log.info("Diagnostics report:\n{}", report);
        return report;
    }

    private static void appendProxy(StringBuilder sb) {
        try {
            List<Proxy> proxies = ProxySelector.getDefault().select(URI.create("https://github.com/"));
            if (proxies == null || proxies.isEmpty()) {
                sb.append("Proxy: direct\n");
                return;
            }
            Proxy p = proxies.getFirst();
            sb.append("Proxy: ").append(p.type());
            if (p.address() instanceof InetSocketAddress addr) {
                sb.append(" ").append(addr.getHostString()).append(":").append(addr.getPort());
            }
            sb.append("\n");
        } catch (Exception e) {
            sb.append("Proxy: (error: ").append(e.getMessage()).append(")\n");
        }
    }

    private static String pathStatus(java.nio.file.Path p) {
        if (p == null) {
            return "missing";
        }
        if (!Files.isRegularFile(p)) {
            return "missing";
        }
        try {
            boolean works = Files.size(p) > 0;
            return p + " (" + Files.size(p) + " bytes" + (works ? ", OK" : "") + ")";
        } catch (Exception e) {
            return p + " (error)";
        }
    }

    private static java.nio.file.Path localCloudflared() {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        return CrewcastPaths.binDir().resolve(win ? "cloudflared.exe" : "cloudflared");
    }

    private static String probeHead(HttpClient client, String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            return "HTTP " + resp.statusCode() + " → " + resp.uri().getHost();
        } catch (Exception e) {
            return DownloadErrors.plainReason(e, url);
        }
    }
}
