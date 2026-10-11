package com.solocrew.crewcast.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class SubtitleCacheKeys {

    private SubtitleCacheKeys() {
    }

    static String cacheFileName(Path video, SubtitleCatalog.Track track) throws IOException {
        long mtime = Files.getLastModifiedTime(video).toMillis();
        long size = Files.size(video);
        String side = "";
        if (track.source() == SubtitleCatalog.Source.EXTERNAL && track.externalPath() != null) {
            Path ext = track.externalPath();
            side = "|ext=" + ext.toAbsolutePath().normalize()
                    + "|em=" + Files.getLastModifiedTime(ext).toMillis()
                    + "|es=" + Files.size(ext);
        }
        String raw = video.toAbsolutePath().normalize() + "|" + mtime + "|" + size + "|sub="
                + track.index() + "|src=" + track.source() + "|emb=" + track.embeddedSubtitleIndex() + side;
        return sha256(raw) + ".vtt";
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
