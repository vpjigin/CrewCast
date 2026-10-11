package com.solocrew.crewcast.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class SubtitleTextEncoding {

    private static final Logger log = LoggerFactory.getLogger(SubtitleTextEncoding.class);

    private SubtitleTextEncoding() {
    }

    static String readAsUtf8(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        Charset charset = detectCharset(bytes);
        String text = new String(bytes, charset);
        if (charset != StandardCharsets.UTF_8) {
            log.info("Subtitle {} decoded as {} ({} bytes)", file.getFileName(), charset.name(), bytes.length);
        }
        return text;
    }

    static Charset detectCharset(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (bytes.length >= 2) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            if (b0 == 0xFF && b1 == 0xFE) {
                return StandardCharsets.UTF_16LE;
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return StandardCharsets.UTF_16BE;
            }
        }
        if (looksUtf8(bytes)) {
            return StandardCharsets.UTF_8;
        }
        try {
            String decoded = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes)).toString();
            if (!decoded.contains("\uFFFD")) {
                return StandardCharsets.UTF_8;
            }
        } catch (Exception ignored) {
        }
        log.warn("Subtitle charset uncertain; assuming UTF-8 ({} bytes)", bytes.length);
        return StandardCharsets.UTF_8;
    }

    private static boolean looksUtf8(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;
            if (b < 0x80) {
                i++;
                continue;
            }
            int needed;
            if ((b & 0xE0) == 0xC0) {
                needed = 1;
            } else if ((b & 0xF0) == 0xE0) {
                needed = 2;
            } else if ((b & 0xF8) == 0xF0) {
                needed = 3;
            } else {
                return false;
            }
            if (i + needed >= bytes.length) {
                return false;
            }
            for (int j = 1; j <= needed; j++) {
                if ((bytes[i + j] & 0xC0) != 0x80) {
                    return false;
                }
            }
            i += needed + 1;
        }
        return true;
    }
}
