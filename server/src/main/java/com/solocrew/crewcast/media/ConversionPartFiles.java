package com.solocrew.crewcast.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class ConversionPartFiles {

    private ConversionPartFiles() {
    }

    static Path partPath(Path finalOutput) {
        return Path.of(finalOutput.getParent().toString(), finalOutput.getFileName().toString() + ".part");
    }

    static void deleteIfExists(Path finalOutput) {
        try {
            Files.deleteIfExists(partPath(finalOutput));
        } catch (IOException ignored) {
        }
    }

    static void deleteStaleCompatParts(Path cacheDir) throws IOException {
        if (!Files.isDirectory(cacheDir)) {
            return;
        }
        try (var stream = Files.list(cacheDir)) {
            for (Path p : stream.toList()) {
                String name = p.getFileName().toString();
                if (name.startsWith("compat-") && name.endsWith(".part")) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    static void deleteStaleAudioParts(Path cacheDir) throws IOException {
        if (!Files.isDirectory(cacheDir)) {
            return;
        }
        try (var stream = Files.list(cacheDir)) {
            for (Path p : stream.toList()) {
                String name = p.getFileName().toString();
                if (name.endsWith(".part") && !name.startsWith("compat-")) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
