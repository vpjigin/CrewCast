package com.solocrew.crewcast.share;

import java.nio.file.Path;

public record SharedFile(String id, String name, Path path, FileKind kind, long sizeBytes, long lastModifiedMillis) {
}
