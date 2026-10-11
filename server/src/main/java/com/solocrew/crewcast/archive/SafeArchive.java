package com.solocrew.crewcast.archive;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
/** Safe extraction of zip and tar archives (zip-slip resistant). */
public final class SafeArchive {

    public static final String ESCAPE_MESSAGE = "Archive entry path escape blocked";

    private static final Set<String> KNOWN_BINARIES =
            Set.of("ffmpeg", "ffprobe", "cloudflared", "ffmpeg.exe", "ffprobe.exe", "cloudflared.exe");

    private SafeArchive() {
    }

    /**
     * Resolve an archive member name under {@code destDir}. The returned path is normalized and
     * guaranteed to stay under the destination root.
     */
    public static Path resolveEntry(Path destDir, String entryName) throws IOException {
        String name = normalizeEntryName(entryName);
        if (name.isEmpty()) {
            throw new IOException(ESCAPE_MESSAGE + ": empty entry name");
        }
        rejectUnsafeEntryName(name);
        Path destRoot = destDir.toAbsolutePath().normalize();
        Files.createDirectories(destRoot);
        Path destReal = realPathOrNormalized(destRoot);
        Path resolved = destReal.resolve(name).normalize();
        if (!resolved.startsWith(destReal)) {
            throw new IOException(ESCAPE_MESSAGE + ": path traversal");
        }
        return resolved;
    }

    public static void extractZip(Path zip, Path destDir) throws IOException {
        extractViaStaging(destDir, staging -> extractZipInto(staging, zip), "zip");
    }

    public static void extractTarGz(Path archive, Path destDir) throws IOException {
        try (InputStream in = new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            extractViaStaging(destDir, staging -> extractTarStreamInto(staging, in), "tar.gz");
        }
    }

    public static void extractTarXz(Path archive, Path destDir) throws IOException {
        try (InputStream in = new XZCompressorInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            extractViaStaging(destDir, staging -> extractTarStreamInto(staging, in), "tar.xz");
        }
    }

    @FunctionalInterface
    private interface StagingExtract {
        void run(Path stagingDir) throws IOException;
    }

    private static void extractViaStaging(Path destDir, StagingExtract extract, String label) throws IOException {
        Path parent = destDir.getParent();
        if (parent == null) {
            parent = destDir.toAbsolutePath().normalize();
        }
        Files.createDirectories(parent);
        Path staging = Files.createTempDirectory(parent, destDir.getFileName() + "-extract-");
        try {
            extract.run(staging);
            mergeStagingIntoDest(staging, destDir);
        } catch (IOException e) {
            deleteRecursive(staging);
            throw wrapExtractFailure(label, e);
        }
        deleteRecursive(staging);
    }

    private static void extractZipInto(Path destDir, Path zip) throws IOException {
        Files.createDirectories(destDir);
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(Files.newInputStream(zip))) {
            ZipArchiveEntry entry;
            while ((entry = zis.getNextZipEntry()) != null) {
                String rawName = entry.getName();
                boolean directory = entry.isDirectory() || (rawName != null && rawName.endsWith("/"));
                if (directory) {
                    Path dir = resolveEntry(destDir, rawName);
                    Files.createDirectories(dir);
                    continue;
                }
                Path out = resolveEntry(destDir, rawName);
                Files.createDirectories(out.getParent());
                copyStream(zis, out);
                applyUnixMode(out, entry.getUnixMode());
            }
        }
    }

    private static void extractTarStreamInto(Path destDir, InputStream tarStream) throws IOException {
        Files.createDirectories(destDir);
        try (TarArchiveInputStream tar = new TarArchiveInputStream(tarStream)) {
            ArchiveEntry rawEntry;
            while ((rawEntry = tar.getNextEntry()) != null) {
                if (!(rawEntry instanceof org.apache.commons.compress.archivers.tar.TarArchiveEntry entry)) {
                    continue;
                }
                String linkName = entry.getLinkName();
                if (entry.isSymbolicLink() || entry.isLink() || (linkName != null && !linkName.isBlank())) {
                    IOUtils.skip(tar, entry.getSize());
                    continue;
                }
                if (!entry.isFile() && !entry.isDirectory()) {
                    IOUtils.skip(tar, entry.getSize());
                    continue;
                }
                Path out = resolveEntry(destDir, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    applyUnixMode(out, entry.getMode());
                } else {
                    Files.createDirectories(out.getParent());
                    copyStream(tar, out);
                    applyUnixMode(out, entry.getMode());
                }
            }
        }
    }

    private static void mergeStagingIntoDest(Path staging, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        try (var walk = Files.walk(staging)) {
            for (Path source : walk.toList()) {
                if (source.equals(staging)) {
                    continue;
                }
                Path relative = staging.relativize(source);
                Path target = destDir.resolve(relative.toString()).normalize();
                Path destRoot = destDir.toAbsolutePath().normalize();
                if (!target.startsWith(destRoot)) {
                    throw new IOException(ESCAPE_MESSAGE + ": path traversal");
                }
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void applyUnixMode(Path path, int unixMode) throws IOException {
        if (isWindows() || unixMode == 0) {
            ensureKnownBinaryExecutable(path);
            return;
        }
        int perms = unixMode & 0777;
        if (perms != 0) {
            try {
                Files.setPosixFilePermissions(path, posixFromMode(perms));
            } catch (UnsupportedOperationException ignored) {
                // non-posix filesystem
            }
        }
        ensureKnownBinaryExecutable(path);
    }

    private static void ensureKnownBinaryExecutable(Path file) throws IOException {
        if (isWindows()) {
            return;
        }
        String name = file.getFileName().toString();
        if (!KNOWN_BINARIES.contains(name)) {
            return;
        }
        if (Files.isExecutable(file)) {
            return;
        }
        try {
            Files.setPosixFilePermissions(
                    file,
                    EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            file.toFile().setExecutable(true);
        }
    }

    private static Set<PosixFilePermission> posixFromMode(int mode) {
        EnumSet<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
        if ((mode & 0400) != 0) {
            perms.add(PosixFilePermission.OWNER_READ);
        }
        if ((mode & 0200) != 0) {
            perms.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((mode & 0100) != 0) {
            perms.add(PosixFilePermission.OWNER_EXECUTE);
        }
        if ((mode & 0040) != 0) {
            perms.add(PosixFilePermission.GROUP_READ);
        }
        if ((mode & 0020) != 0) {
            perms.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((mode & 0010) != 0) {
            perms.add(PosixFilePermission.GROUP_EXECUTE);
        }
        if ((mode & 0004) != 0) {
            perms.add(PosixFilePermission.OTHERS_READ);
        }
        if ((mode & 0002) != 0) {
            perms.add(PosixFilePermission.OTHERS_WRITE);
        }
        if ((mode & 0001) != 0) {
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        return perms;
    }

    private static void copyStream(InputStream in, Path out) throws IOException {
        try (OutputStream os = Files.newOutputStream(out)) {
            in.transferTo(os);
        }
    }

    private static String normalizeEntryName(String entryName) {
        if (entryName == null) {
            return "";
        }
        String name = entryName.replace('\\', '/');
        while (name.endsWith("/")) {
            name = name.substring(0, name.length() - 1);
        }
        return name;
    }

    private static void rejectUnsafeEntryName(String name) throws IOException {
        if (name.indexOf('\0') >= 0) {
            throw new IOException(ESCAPE_MESSAGE + ": NUL in entry name");
        }
        if (name.startsWith("/")) {
            throw new IOException(ESCAPE_MESSAGE + ": absolute path");
        }
        if (name.length() >= 2 && name.charAt(1) == ':' && Character.isLetter(name.charAt(0))) {
            throw new IOException(ESCAPE_MESSAGE + ": drive letter path");
        }
    }

    private static Path realPathOrNormalized(Path path) throws IOException {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static IOException wrapExtractFailure(String kind, IOException e) {
        if (e.getMessage() != null && e.getMessage().contains(ESCAPE_MESSAGE)) {
            return e;
        }
        return new IOException("Could not unpack the " + kind, e);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
