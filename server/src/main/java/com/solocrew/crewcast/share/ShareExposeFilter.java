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
        if (!candidate.startsWith(root) || !SharePathSecurity.isInsideShareRoot(root, candidate)) {
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

    public static boolean isIgnoredDirectoryName(String name) {
        return IGNORED_DIR_NAMES.contains(name);
    }

    /** Whether a directory entry should appear in browse listings (not file expose rules). */
    public static boolean shouldListDirectoryEntry(Path shareRoot, Path directory) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        String name = directory.getFileName().toString();
        if (isHiddenFileName(name) || isIgnoredDirectoryName(name)) {
            return false;
        }
        try {
            if (hasOsHiddenAttribute(directory)) {
                return false;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return SharePathSecurity.isInsideShareRoot(shareRoot, directory);
    }

    /** Whether a file or directory entry under {@code parent} should appear in immediate children. */
    public static boolean shouldListEntry(Path shareRoot, Path entry) {
        if (Files.isRegularFile(entry)) {
            return shouldExposeFromFolder(shareRoot, entry);
        }
        if (Files.isDirectory(entry)) {
            return shouldListDirectoryEntry(shareRoot, entry);
        }
        return false;
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
