package com.solocrew.crewcast.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class AudioCacheKeys {

    private AudioCacheKeys() {
    }

    static String cacheFileName(Path source, int audioIndex) throws IOException {
        long mtime = Files.getLastModifiedTime(source).toMillis();
        long size = Files.size(source);
        String raw = source.toAbsolutePath().normalize() + "|" + mtime + "|" + size + "|" + audioIndex;
        return sha256(raw) + ".mp4";
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
