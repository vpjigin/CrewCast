package com.solocrew.crewcast.share;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;

/**
 * Zip archive writer: STORED (method 0), UTF-8 names, data descriptors (GP bit 3), Zip64 layout throughout.
 */
public final class StreamingStoredZipWriter {

    private static final Logger log = LoggerFactory.getLogger(StreamingStoredZipWriter.class);

    private static final int GP_UTF8 = 0x0800;
    private static final int GP_DATA_DESCRIPTOR = 0x0008;
    private static final int GP_FLAGS = GP_UTF8 | GP_DATA_DESCRIPTOR;

    private static final int LOCAL_HEADER_FIXED = 30;
    private static final int CD_HEADER_FIXED = 46;
    private static final int DATA_DESCRIPTOR_SIZE = 24;
    private static final int ZIP64_EXTRA_HEADER = 4;
    private static final int ZIP64_LOCAL_EXTRA_DATA = 16;
    private static final int ZIP64_CD_EXTRA_DATA = 24;
    private static final int ZIP64_EOCD_SIZE = 56;
    private static final int ZIP64_LOCATOR_SIZE = 20;
    private static final int EOCD_SIZE = 22;

    private static final int BUFFER_SIZE = 64 * 1024;

    private StreamingStoredZipWriter() {
    }

    public record PlannedEntry(String entryName, byte[] nameUtf8, Path file, long size) {
        public PlannedEntry {
            if (nameUtf8 == null) {
                nameUtf8 = entryName.getBytes(StandardCharsets.UTF_8);
            }
        }

        public static PlannedEntry of(String entryName, Path file, long size) {
            return new PlannedEntry(entryName, entryName.getBytes(StandardCharsets.UTF_8), file, size);
        }
    }

    public record WrittenEntry(
            PlannedEntry plan,
            long localHeaderOffset,
            long crc32,
            long size) {
    }

    public static long totalArchiveSize(List<PlannedEntry> entries) {
        long offset = 0;
        for (PlannedEntry e : entries) {
            int localExtra = zip64LocalExtraSize();
            offset += LOCAL_HEADER_FIXED + e.nameUtf8().length + localExtra + e.size() + DATA_DESCRIPTOR_SIZE;
        }
        long cdOffset = offset;
        long cdSize = 0;
        for (PlannedEntry e : entries) {
            cdSize += CD_HEADER_FIXED + e.nameUtf8().length + zip64CentralExtraSize();
        }
        return cdOffset + cdSize + ZIP64_EOCD_SIZE + ZIP64_LOCATOR_SIZE + EOCD_SIZE;
    }

    public static void writeArchive(
            OutputStream rawOut,
            List<PlannedEntry> entries,
            BooleanSupplier clientConnected) throws IOException {
        List<WrittenEntry> written = new ArrayList<>(entries.size());
        long offset = 0;
        for (PlannedEntry plan : entries) {
            if (!clientConnected.getAsBoolean()) {
                throw new IOException("Client disconnected");
            }
            written.add(writeLocalEntry(rawOut, plan, offset, clientConnected));
            offset = written.getLast().localHeaderOffset()
                    + LOCAL_HEADER_FIXED
                    + plan.nameUtf8().length
                    + zip64LocalExtraSize()
                    + plan.size()
                    + DATA_DESCRIPTOR_SIZE;
        }
        long cdOffset = offset;
        long cdSize = 0;
        for (WrittenEntry w : written) {
            if (!clientConnected.getAsBoolean()) {
                throw new IOException("Client disconnected");
            }
            writeCentralDirectoryHeader(rawOut, w);
            cdSize += CD_HEADER_FIXED + w.plan().nameUtf8().length + zip64CentralExtraSize();
        }
        long zip64EocdOffset = cdOffset + cdSize;
        writeZip64EndOfCentralDirectory(rawOut, entries.size(), cdSize, cdOffset);
        writeZip64Locator(rawOut, zip64EocdOffset);
        writeEndOfCentralDirectory(rawOut);
    }

    private static WrittenEntry writeLocalEntry(
            OutputStream out,
            PlannedEntry plan,
            long localHeaderOffset,
            BooleanSupplier clientConnected) throws IOException {
        writeLocalFileHeader(out, plan);
        CRC32 crc = new CRC32();
        long bytesWritten = 0;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(plan.file()))) {
            byte[] buf = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buf)) >= 0) {
                if (!clientConnected.getAsBoolean()) {
                    throw new IOException("Client disconnected");
                }
                if (read > 0) {
                    out.write(buf, 0, read);
                    crc.update(buf, 0, read);
                    bytesWritten += read;
                }
            }
        } catch (IOException ex) {
            log.warn("Failed reading file for zip entry {}: {}", plan.entryName(), ex.getMessage());
            throw ex;
        }
        if (bytesWritten != plan.size()) {
            log.error(
                    "Zip entry size mismatch for {}: expected {} bytes, read {}",
                    plan.entryName(),
                    plan.size(),
                    bytesWritten);
            throw new IOException("File size changed while building zip: " + plan.entryName());
        }
        long crcValue = crc.getValue();
        writeDataDescriptor(out, crcValue, plan.size());
        return new WrittenEntry(plan, localHeaderOffset, crcValue, plan.size());
    }

    private static void writeLocalFileHeader(OutputStream out, PlannedEntry plan) throws IOException {
        writeInt(out, 0x04034b50);
        writeShort(out, 45);
        writeShort(out, GP_FLAGS);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, 0);
        writeInt(out, 0xFFFFFFFF);
        writeInt(out, 0xFFFFFFFF);
        writeShort(out, plan.nameUtf8().length);
        writeShort(out, zip64LocalExtraSize());
        out.write(plan.nameUtf8());
        writeZip64LocalExtra(out);
    }

    private static void writeZip64LocalExtra(OutputStream out) throws IOException {
        writeShort(out, 0x0001);
        writeShort(out, ZIP64_LOCAL_EXTRA_DATA);
        writeLong(out, 0);
        writeLong(out, 0);
    }

    private static void writeDataDescriptor(OutputStream out, long crc32, long size) throws IOException {
        writeInt(out, 0x08074b50);
        writeInt(out, (int) crc32);
        writeLong(out, size);
        writeLong(out, size);
    }

    private static void writeCentralDirectoryHeader(OutputStream out, WrittenEntry w) throws IOException {
        PlannedEntry plan = w.plan();
        writeInt(out, 0x02014b50);
        writeShort(out, 45);
        writeShort(out, 45);
        writeShort(out, GP_FLAGS);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, (int) w.crc32());
        writeInt(out, 0xFFFFFFFF);
        writeInt(out, 0xFFFFFFFF);
        writeShort(out, plan.nameUtf8().length);
        writeShort(out, zip64CentralExtraSize());
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, 0);
        writeInt(out, 0xFFFFFFFF);
        out.write(plan.nameUtf8());
        writeZip64CentralExtra(out, w.size(), w.localHeaderOffset());
    }

    private static void writeZip64CentralExtra(OutputStream out, long size, long localOffset) throws IOException {
        writeShort(out, 0x0001);
        writeShort(out, ZIP64_CD_EXTRA_DATA);
        writeLong(out, size);
        writeLong(out, size);
        writeLong(out, localOffset);
    }

    private static void writeZip64EndOfCentralDirectory(
            OutputStream out, long entryCount, long cdSize, long cdOffset) throws IOException {
        writeInt(out, 0x06064b50);
        writeLong(out, 44);
        writeShort(out, 45);
        writeShort(out, 45);
        writeInt(out, 0);
        writeInt(out, 0);
        writeLong(out, entryCount);
        writeLong(out, entryCount);
        writeLong(out, cdSize);
        writeLong(out, cdOffset);
    }

    private static void writeZip64Locator(OutputStream out, long zip64EocdOffset) throws IOException {
        writeInt(out, 0x07064b50);
        writeInt(out, 0);
        writeLong(out, zip64EocdOffset);
        writeInt(out, 1);
    }

    private static void writeEndOfCentralDirectory(OutputStream out) throws IOException {
        writeInt(out, 0x06054b50);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0xFFFF);
        writeShort(out, 0xFFFF);
        writeInt(out, 0xFFFFFFFF);
        writeInt(out, 0xFFFFFFFF);
        writeShort(out, 0);
    }

    private static int zip64LocalExtraSize() {
        return ZIP64_EXTRA_HEADER + ZIP64_LOCAL_EXTRA_DATA;
    }

    private static int zip64CentralExtraSize() {
        return ZIP64_EXTRA_HEADER + ZIP64_CD_EXTRA_DATA;
    }

    private static void writeInt(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
    }

    private static void writeShort(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }

    private static void writeLong(OutputStream out, long v) throws IOException {
        writeInt(out, (int) (v & 0xFFFFFFFFL));
        writeInt(out, (int) ((v >>> 32) & 0xFFFFFFFFL));
    }
}
