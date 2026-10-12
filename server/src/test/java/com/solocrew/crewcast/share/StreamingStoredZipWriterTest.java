package com.solocrew.crewcast.share;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingStoredZipWriterTest {

    @TempDir
    Path tmp;

    @Test
    void predictedSizeMatchesWrittenBytesAndZipFileReadsContent() throws Exception {
        Path a = tmp.resolve("a.txt");
        Path sub = Files.createDirectories(tmp.resolve("nested"));
        Path b = sub.resolve("b.txt");
        Files.writeString(a, "alpha", StandardCharsets.UTF_8);
        Files.writeString(b, "beta", StandardCharsets.UTF_8);

        List<StreamingStoredZipWriter.PlannedEntry> plans = List.of(
                StreamingStoredZipWriter.PlannedEntry.of("a.txt", a, Files.size(a)),
                StreamingStoredZipWriter.PlannedEntry.of("nested/b.txt", b, Files.size(b)));

        long predicted = StreamingStoredZipWriter.totalArchiveSize(plans);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        StreamingStoredZipWriter.writeArchive(bos, plans, () -> true);
        byte[] zipBytes = bos.toByteArray();
        assertEquals(predicted, zipBytes.length);

        Path zipPath = tmp.resolve("out.zip");
        Files.write(zipPath, zipBytes);
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            assertEquals("alpha", new String(zf.getInputStream(zf.getEntry("a.txt")).readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("beta", new String(zf.getInputStream(zf.getEntry("nested/b.txt")).readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void emptyEntryStillProducesValidZip() throws Exception {
        Path empty = tmp.resolve("empty.bin");
        Files.write(empty, new byte[0]);
        List<StreamingStoredZipWriter.PlannedEntry> plans = List.of(
                StreamingStoredZipWriter.PlannedEntry.of("empty.bin", empty, 0));
        long predicted = StreamingStoredZipWriter.totalArchiveSize(plans);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        StreamingStoredZipWriter.writeArchive(bos, plans, () -> true);
        assertEquals(predicted, bos.size());
    }

    @Test
    void sizeMismatchAbortsWrite() throws Exception {
        Path f = tmp.resolve("f.txt");
        Files.writeString(f, "12345", StandardCharsets.UTF_8);
        List<StreamingStoredZipWriter.PlannedEntry> plans = List.of(
                StreamingStoredZipWriter.PlannedEntry.of("f.txt", f, 99));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> StreamingStoredZipWriter.writeArchive(bos, plans, () -> true));
    }

    @Test
    void zip64SizeThresholdInflatesPredictedArchiveSize() {
        List<StreamingStoredZipWriter.PlannedEntry> plans = List.of(
                StreamingStoredZipWriter.PlannedEntry.of(
                        "large.bin",
                        tmp.resolve("large.bin"),
                        5L * 1024 * 1024 * 1024));
        long size = StreamingStoredZipWriter.totalArchiveSize(plans);
        assertTrue(size > 5L * 1024 * 1024 * 1024);
    }
}
