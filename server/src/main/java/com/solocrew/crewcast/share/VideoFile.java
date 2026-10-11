package com.solocrew.crewcast.share;

import java.nio.file.Path;

public record VideoFile(String id, String displayName, Path path) {
}
