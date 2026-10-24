package com.solocrew.crewcast.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class CompatCacheKeys {

    private CompatCacheKeys() {
    }

    /** Cache identity depends on source identity + transcode policy, not the computed bitrate. */
    static String cacheFileName(Path source, int audioIndex, int maxHeight) throws IOException {
        long mtime = Files.getLastModifiedTime(source).toMillis();
        long size = Files.size(source);
        String raw = source.toAbsolutePath().normalize()
                + "|" + mtime + "|" + size + "|" + audioIndex + "|compat-h264-" + maxHeight
                + "|" + CompatVideoBitrate.CACHE_POLICY_TAG;
        return "compat-" + sha256(raw) + ".mp4";
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
