package com.solocrew.crewcast;

import com.solocrew.crewcast.share.ShareZipDownloadService;
import com.solocrew.crewcast.web.ContentDispositionHelper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class DownloadAllController {

    private final ShareZipDownloadService zipDownloadService;

    public DownloadAllController(ShareZipDownloadService zipDownloadService) {
        this.zipDownloadService = zipDownloadService;
    }

    @GetMapping("/api/download-all")
    public void downloadAll(
            @RequestParam(name = "folder", required = false) String folder,
            HttpServletResponse response) throws IOException {
        ShareZipDownloadService.PreparedDownload prepared = zipDownloadService.prepareDownload(folder);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDispositionHelper.attachmentFilename(prepared.archiveName()).toString());
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "none");
        response.setContentLengthLong(prepared.contentLength());
        zipDownloadService.writeZip(prepared, response.getOutputStream(), () -> !Thread.currentThread().isInterrupted());
        response.flushBuffer();
    }
}
