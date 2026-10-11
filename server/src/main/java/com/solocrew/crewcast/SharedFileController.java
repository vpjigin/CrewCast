package com.solocrew.crewcast;

import com.solocrew.crewcast.share.FileKind;
import com.solocrew.crewcast.share.ShareBrowseService;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.share.SharedFile;
import com.solocrew.crewcast.web.ContentDispositionHelper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
public class SharedFileController {

    private final ShareRegistry shareRegistry;
    private final ShareBrowseService shareBrowseService;

    public SharedFileController(ShareRegistry shareRegistry, ShareBrowseService shareBrowseService) {
        this.shareRegistry = shareRegistry;
        this.shareBrowseService = shareBrowseService;
    }

    @GetMapping("/api/browse")
    public ShareBrowseService.BrowseResponse browse(
            @org.springframework.web.bind.annotation.RequestParam(name = "folder", required = false) String folder)
            throws IOException {
        return shareBrowseService.browse(folder);
    }

    @GetMapping("/api/files")
    public LibraryResponse listFiles() throws IOException {
        List<SharedFile> files = shareRegistry.listSharedFiles();
        List<FileSummary> items = files.stream().map(this::toSummary).toList();
        return new LibraryResponse(
                shareRegistry.primaryShareTitle(),
                items.size(),
                items);
    }

    @GetMapping("/file/{id}")
    public ResponseEntity<Resource> serveFile(
            @PathVariable String id,
            @RequestParam(name = "download", defaultValue = "false") boolean download) throws IOException {

        SharedFile file = shareRegistry.findSharedFileById(id).orElse(null);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = file.path();
        if (!Files.exists(path) || !shareRegistry.isSharedPathAllowed(path)) {
            return ResponseEntity.notFound().build();
        }

        Resource body = new FileSystemResource(path);
        MediaType mediaType = MediaType.parseMediaType(contentTypeFor(file.name()));
        var builder = ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(file.sizeBytes())
                .lastModified(file.lastModifiedMillis());

        if (download) {
            builder.header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDispositionHelper.attachmentFilename(file.name()).toString());
        }
        return builder.body(body);
    }

    private FileSummary toSummary(SharedFile file) {
        return new FileSummary(
                file.id(),
                file.name(),
                file.kind().name(),
                file.kind().label(),
                FileKind.extensionLabel(file.name()),
                file.sizeBytes(),
                file.lastModifiedMillis(),
                file.kind().viewableInBrowser());
    }

    static String contentTypeFor(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".zip")) {
            return "application/zip";
        }
        if (ShareRegistry.isVideoFileName(name)) {
            return "application/octet-stream";
        }
        return "application/octet-stream";
    }

    public record LibraryResponse(String shareTitle, int fileCount, List<FileSummary> files) {
    }

    public record FileSummary(
            String id,
            String name,
            String kindKey,
            String kind,
            String ext,
            long sizeBytes,
            long modifiedMillis,
            boolean viewable) {
    }
}
