package com.solocrew.crewcast.net;

import com.solocrew.crewcast.system.CrewcastPaths;

import java.nio.file.Path;

/** User-facing download/tunnel failure context for copy-to-clipboard. */
public record DownloadFailureDetails(String reason, String url, Path logFile) {

    public static DownloadFailureDetails of(String reason, String url) {
        return new DownloadFailureDetails(
                reason != null ? reason : "Download failed",
                url != null ? url : "",
                CrewcastPaths.logFile());
    }

    public String copyText() {
        StringBuilder sb = new StringBuilder();
        sb.append(reason);
        if (url != null && !url.isBlank()) {
            sb.append("\nURL: ").append(url);
        }
        if (logFile != null) {
            sb.append("\nLog: ").append(logFile.toAbsolutePath());
        }
        return sb.toString();
    }
}
