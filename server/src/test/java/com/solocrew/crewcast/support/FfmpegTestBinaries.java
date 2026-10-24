package com.solocrew.crewcast.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/** Minimal executable ffmpeg/ffprobe stubs under {@code crewcast.home}/bin for Spring tests. */
public final class FfmpegTestBinaries {

    private FfmpegTestBinaries() {}

    public static void installStubTools(Path crewcastHome) throws IOException {
        Path bin = crewcastHome.resolve("bin");
        Files.createDirectories(bin);
        for (String name : new String[] {"ffmpeg", "ffprobe"}) {
            Path script = bin.resolve(name);
            Files.writeString(script, "#!/bin/sh\nexit 0\n");
            makeExecutable(script);
        }
    }

    public static void removeStubTools(Path crewcastHome) throws IOException {
        Path bin = crewcastHome.resolve("bin");
        Files.deleteIfExists(bin.resolve("ffmpeg"));
        Files.deleteIfExists(bin.resolve("ffprobe"));
    }

    private static void makeExecutable(Path script) throws IOException {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(script, perms);
        } catch (UnsupportedOperationException ignored) {
            script.toFile().setExecutable(true);
        }
    }
}
