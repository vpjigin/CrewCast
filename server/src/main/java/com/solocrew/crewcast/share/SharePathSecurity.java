package com.solocrew.crewcast.share;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class SharePathSecurity {

    private SharePathSecurity() {
    }

    static boolean isInsideShareRoot(Path shareRoot, Path candidate) {
        Path root = shareRoot.toAbsolutePath().normalize();
        Path path = candidate.toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            return false;
        }
        Path rootReal;
        Path pathReal;
        try {
            rootReal = root.toRealPath();
            pathReal = path.toRealPath();
        } catch (IOException e) {
            return false;
        }
        return pathReal.startsWith(rootReal);
    }
}
