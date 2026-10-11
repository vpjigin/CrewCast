package com.solocrew.crewcast.archive;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeArchiveTest {

    @TempDir
    Path tmp;

    @Test
    void extractsNestedZipSafely() throws Exception {
        Path dest = tmp.resolve("out");
        Path zip = tmp.resolve("good.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("nested/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("nested/hello.txt"));
            zos.write("hi".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("nested/sub/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("nested/sub/ok.txt"));
            zos.write("ok".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        SafeArchive.extractZip(zip, dest);

        assertEquals("hi", Files.readString(dest.resolve("nested/hello.txt")));
        assertEquals("ok", Files.readString(dest.resolve("nested/sub/ok.txt")));
    }

    @Test
    void rejectsZipPathTraversalFile() throws Exception {
        Path dest = tmp.resolve("in");
        Path leak = tmp.resolve("evil-leaked.txt");
        Path zip = maliciousZip("../evil-leaked.txt", "pwnd", false);

        IOException ex = assertThrows(IOException.class, () -> SafeArchive.extractZip(zip, dest));
        assertTrue(ex.getMessage().contains(SafeArchive.ESCAPE_MESSAGE));
        assertFalse(Files.exists(leak));
        assertFalse(Files.exists(dest.resolve("evil-leaked.txt")));
    }

    @Test
    void rejectsZipPathTraversalDirectory() throws Exception {
        Path dest = tmp.resolve("in");
        Path zip = maliciousZip("../../outside/", null, true);

        assertThrows(IOException.class, () -> SafeArchive.extractZip(zip, dest));
        assertFalse(Files.exists(tmp.getParent().resolve("outside")));
    }

    @Test
    void rejectsZipAbsolutePath() throws Exception {
        Path dest = tmp.resolve("in");
        Path zip = maliciousZip("/tmp/evil.txt", "x", false);

        assertThrows(IOException.class, () -> SafeArchive.extractZip(zip, dest));
        assertFalse(Files.exists(Path.of("/tmp/evil.txt")));
    }

    @Test
    void rejectsWindowsStyleZipEntry() throws Exception {
        Path dest = tmp.resolve("in");
        Path zip = maliciousZip("..\\evil.txt", "x", false);

        assertThrows(IOException.class, () -> SafeArchive.extractZip(zip, dest));
    }

    @Test
    void rejectsTarPathTraversal() throws Exception {
        Path dest = tmp.resolve("in");
        Path tgz = maliciousTarGz("../evil.txt", "bad");

        assertThrows(IOException.class, () -> SafeArchive.extractTarGz(tgz, dest));
        assertFalse(Files.exists(tmp.resolve("evil.txt")));
    }

    @Test
    void preExistingDestFilesSurviveFailedExtraction() throws Exception {
        Path dest = tmp.resolve("bin");
        Files.createDirectories(dest);
        Files.writeString(dest.resolve("keep.txt"), "keep");
        Path zip = maliciousZip("../evil.txt", "pwnd", false);

        assertThrows(IOException.class, () -> SafeArchive.extractZip(zip, dest));
        assertEquals("keep", Files.readString(dest.resolve("keep.txt")));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void zipKnownBinaryIsExecutable() throws Exception {
        Path dest = tmp.resolve("out");
        Path zip = tmp.resolve("exec.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            ZipArchiveEntry entry = new ZipArchiveEntry("cloudflared");
            entry.setUnixMode(0100755);
            zos.putArchiveEntry(entry);
            zos.write("bin".getBytes(StandardCharsets.UTF_8));
            zos.closeArchiveEntry();
        }
        SafeArchive.extractZip(zip, dest);
        assertTrue(Files.isExecutable(dest.resolve("cloudflared")));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void tarGzExecutableEntryIsExecutable() throws Exception {
        Path dest = tmp.resolve("out");
        Path tgz = tmp.resolve("exec.tgz");
        try (OutputStream fos = Files.newOutputStream(tgz);
                GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(fos);
                TarArchiveOutputStream tar = new TarArchiveOutputStream(gzos)) {
            TarArchiveEntry entry = new TarArchiveEntry("tool");
            entry.setMode(0100755);
            byte[] data = "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8);
            entry.setSize(data.length);
            tar.putArchiveEntry(entry);
            tar.write(data);
            tar.closeArchiveEntry();
        }
        SafeArchive.extractTarGz(tgz, dest);
        assertTrue(Files.isExecutable(dest.resolve("tool")));
    }

    @Test
    void skipsTarSymlinkEntries() throws Exception {
        Path dest = tmp.resolve("in");
        Path secret = tmp.resolve("secret.txt");
        Files.writeString(secret, "secret");
        Path tgz = tmp.resolve("link.tgz");
        try (OutputStream fos = Files.newOutputStream(tgz);
                GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(fos);
                TarArchiveOutputStream tar = new TarArchiveOutputStream(gzos)) {
            TarArchiveEntry sym = new TarArchiveEntry("link.txt", true);
            sym.setLinkName(secret.toString());
            tar.putArchiveEntry(sym);
            tar.closeArchiveEntry();
        }

        SafeArchive.extractTarGz(tgz, dest);
        assertFalse(Files.exists(dest.resolve("link.txt")));
        assertFalse(Files.readString(secret).isEmpty());
    }

    private static Path maliciousZip(String entryName, String content, boolean directory) throws IOException {
        Path zip = Files.createTempFile("malicious-", ".zip");
        zip.toFile().deleteOnExit();
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            String name = directory && !entryName.endsWith("/") ? entryName + "/" : entryName;
            ZipEntry entry = new ZipEntry(name);
            zos.putNextEntry(entry);
            if (content != null) {
                zos.write(content.getBytes(StandardCharsets.UTF_8));
            }
            zos.closeEntry();
        }
        return zip;
    }

    private Path maliciousTarGz(String entryName, String content) throws IOException {
        Path tgz = tmp.resolve("bad.tgz");
        try (OutputStream fos = Files.newOutputStream(tgz);
                GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(fos);
                TarArchiveOutputStream tar = new TarArchiveOutputStream(gzos)) {
            TarArchiveEntry entry = new TarArchiveEntry(entryName);
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            entry.setSize(data.length);
            tar.putArchiveEntry(entry);
            tar.write(data);
            tar.closeArchiveEntry();
        }
        return tgz;
    }
}
