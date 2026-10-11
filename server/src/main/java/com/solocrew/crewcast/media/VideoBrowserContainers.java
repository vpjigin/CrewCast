package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.ShareEntry;
import com.solocrew.crewcast.share.ShareRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Which video containers browsers play reliably without ffmpeg remux. */
public final class VideoBrowserContainers {

    private VideoBrowserContainers() {
    }

    public static boolean isBrowserNativeContainer(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp4") || lower.endsWith(".m4v") || lower.endsWith(".mov");
    }

    /**
     * True when the added share includes at least one video that is not mp4/m4v/mov
     * (for example mkv, webm, avi if ever supported).
     */
    public static boolean addedShareIncludesNonNativeVideo(Path path, ShareEntry.Kind kind) throws IOException {
        if (kind == ShareEntry.Kind.FILE) {
            String name = path.getFileName().toString();
            if (!ShareRegistry.isVideoFileName(name)) {
                return false;
            }
            return !isBrowserNativeContainer(name);
        }
        if (!Files.isDirectory(path)) {
            return false;
        }
        try (var walk = Files.walk(path)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(ShareRegistry::isVideoFileName)
                    .anyMatch(name -> !isBrowserNativeContainer(name));
        }
    }
}
