package com.solocrew.crewcast.support;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/** Minimal executable ffmpeg/ffprobe stubs under {@code crewcast.home}/bin for Spring tests. */
public final class FfmpegTestBinaries {

    static final String STUB_SCRIPT = "#!/bin/sh\nexit 0\n";

    private FfmpegTestBinaries() {}

    public static void installStubTools(Path crewcastHome) throws IOException {
        Path bin = crewcastHome.resolve("bin");
        Files.createDirectories(bin);
        for (String name : new String[] {"ffmpeg", "ffprobe"}) {
            installStubScript(bin.resolve(name));
        }
    }

    private static void installStubScript(Path script) throws IOException {
        if (stubAlreadyInstalled(script)) {
            return;
        }
        Path parent = script.getParent();
        Path temp = Files.createTempFile(parent, script.getFileName().toString() + ".", ".tmp");
        try {
            Files.writeString(temp, STUB_SCRIPT);
            makeExecutable(temp);
            try {
                Files.move(temp, script, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, script, StandardCopyOption.REPLACE_EXISTING);
            }
            makeExecutable(script);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean stubAlreadyInstalled(Path script) throws IOException {
        return Files.isRegularFile(script)
                && Files.isExecutable(script)
                && STUB_SCRIPT.equals(Files.readString(script));
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
