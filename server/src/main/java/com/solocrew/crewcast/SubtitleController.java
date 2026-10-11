package com.solocrew.crewcast;

import com.solocrew.crewcast.media.SubtitleService;
import com.solocrew.crewcast.share.ShareRegistry;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
@RestController
public class SubtitleController {

    private final ShareRegistry shareRegistry;
    private final SubtitleService subtitleService;

    public SubtitleController(ShareRegistry shareRegistry, SubtitleService subtitleService) {
        this.shareRegistry = shareRegistry;
        this.subtitleService = subtitleService;
    }

    @GetMapping("/subtitles/{id}/{index}.vtt")
    public ResponseEntity<Resource> serveVtt(@PathVariable String id, @PathVariable int index) throws Exception {
        Path path = shareRegistry.resolvePath(id).orElse(null);
        if (path == null || !Files.exists(path) || !shareRegistry.isPathAllowed(path)) {
            return ResponseEntity.notFound().build();
        }
        var vtt = subtitleService.resolveVtt(path, index);
        if (vtt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Path file = vtt.get();
        long lastModified = Files.getLastModifiedTime(file).toMillis();
        Resource body = new FileSystemResource(file);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .header(HttpHeaders.CONTENT_TYPE, "text/vtt; charset=UTF-8")
                .lastModified(lastModified)
                .body(body);
    }
}
