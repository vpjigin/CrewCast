package com.solocrew.crewcast.share;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributes;
import java.util.Set;

/** Rules for which paths under a shared folder are exposed on the web. */
public final class ShareExposeFilter {

    private static final Set<String> IGNORED_DIR_NAMES = Set.of(".git", "node_modules");

    private ShareExposeFilter() {
    }

    public static boolean shouldExposeFromFolder(Path folderRoot, Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        Path root = folderRoot.toAbsolutePath().normalize();
        Path candidate = file.toAbsolutePath().normalize();
        if (!candidate.startsWith(root)) {
            return false;
        }
        Path relative = root.relativize(candidate);
        for (int i = 0; i < relative.getNameCount(); i++) {
            if (IGNORED_DIR_NAMES.contains(relative.getName(i).toString())) {
                return false;
            }
        }
        String name = candidate.getFileName().toString();
        if (isHiddenFileName(name) || isJunkFileName(name)) {
            return false;
        }
        return !hasOsHiddenAttribute(candidate);
    }

    public static boolean isHiddenFileName(String name) {
        return name.startsWith(".");
    }

    public static boolean isJunkFileName(String name) {
        return "Thumbs.db".equalsIgnoreCase(name)
                || "desktop.ini".equalsIgnoreCase(name)
                || ".DS_Store".equals(name);
    }

    private static boolean hasOsHiddenAttribute(Path path) {
        try {
            DosFileAttributes attrs = Files.readAttributes(path, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attrs.isHidden() || attrs.isSystem();
        } catch (UnsupportedOperationException | IOException e) {
            return false;
        }
    }
}
