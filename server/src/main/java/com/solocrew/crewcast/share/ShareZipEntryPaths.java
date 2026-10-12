package com.solocrew.crewcast.share;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Zip entry names under a download root (zip-slip safe, UTF-8). */
public final class ShareZipEntryPaths {

    private ShareZipEntryPaths() {
    }

    public static String sanitizeRelativePath(String relative) throws IOException {
        if (relative == null || relative.isBlank()) {
            throw new IOException("Empty zip entry path");
        }
        String normalized = relative.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            throw new IOException("Empty zip entry path");
        }
        if (normalized.indexOf('\0') >= 0) {
            throw new IOException("Invalid zip entry path");
        }
        if (normalized.length() >= 2 && normalized.charAt(1) == ':' && Character.isLetter(normalized.charAt(0))) {
            throw new IOException("Invalid zip entry path");
        }
        String[] parts = normalized.split("/");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                throw new IOException("Invalid zip entry path");
            }
            if (!out.isEmpty()) {
                out.append('/');
            }
            out.append(part);
        }
        if (out.isEmpty()) {
            throw new IOException("Empty zip entry path");
        }
        return out.toString();
    }

    public static String fromRelativePath(Path downloadRoot, Path file) throws IOException {
        Path root = downloadRoot.toAbsolutePath().normalize();
        Path abs = file.toAbsolutePath().normalize();
        Path rel = root.relativize(abs);
        if (rel.isAbsolute() || rel.startsWith("..")) {
            throw new IOException("Path outside download root");
        }
        return sanitizeRelativePath(rel.toString().replace('\\', '/'));
    }

    /** Dedupe collisions among zip root-level entry names (files only). */
    public static String dedupeTopLevel(String entryPath, Map<String, Integer> topLevelUsage) {
        int slash = entryPath.indexOf('/');
        if (slash >= 0) {
            return entryPath;
        }
        String lower = entryPath.toLowerCase(Locale.ROOT);
        Integer used = topLevelUsage.get(lower);
        if (used == null) {
            topLevelUsage.put(lower, 1);
            return entryPath;
        }
        int next = used + 1;
        topLevelUsage.put(lower, next);
        return insertSuffixBeforeExtension(entryPath, " (" + next + ")");
    }

    private static String insertSuffixBeforeExtension(String name, String suffix) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return name + suffix;
        }
        return name.substring(0, dot) + suffix + name.substring(dot);
    }

    public static String archiveDownloadName(String baseName) {
        String safe = baseName == null || baseName.isBlank() ? "Crewcast-shared" : baseName.trim();
        safe = safe.replace('/', '-').replace('\\', '-');
        if (!safe.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            safe = safe + ".zip";
        }
        return safe;
    }
}
