package com.solocrew.crewcast.share;

import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.settings.ShareEntry;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

@Service
public class ShareZipDownloadService {

    public static final String TOO_MANY_MESSAGE =
            "Too many download-all requests are in progress. Try again in a moment.";
    public static final String TOP_LEVEL_ARCHIVE = "Crewcast-shared.zip";

    private static final int BUFFER_SIZE = 64 * 1024;

    private final SettingsStore settingsStore;
    private final ShareBrowseService shareBrowseService;
    private final ShareRegistry shareRegistry;
    private final ShareZipConcurrencyGate gate;
    private final int maxDepth;
    private final int maxEntries;

    public ShareZipDownloadService(
            SettingsStore settingsStore,
            ShareBrowseService shareBrowseService,
            ShareRegistry shareRegistry,
            @Value("${crewcast.download-all.max-concurrent:2}") int maxConcurrent,
            @Value("${crewcast.download-all.max-depth:64}") int maxDepth,
            @Value("${crewcast.download-all.max-entries:10000}") int maxEntries) {
        this.settingsStore = settingsStore;
        this.shareBrowseService = shareBrowseService;
        this.shareRegistry = shareRegistry;
        this.gate = new ShareZipConcurrencyGate(maxConcurrent);
        this.maxDepth = maxDepth;
        this.maxEntries = maxEntries;
    }

    public ShareZipConcurrencyGate concurrencyGateForTests() {
        return gate;
    }

    public String archiveFilenameFor(String folderId) throws IOException {
        return resolveScope(folderId).archiveName();
    }

    public void writeZip(String folderId, OutputStream output, BooleanSupplier clientConnected) throws IOException {
        if (!gate.tryEnter()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_MESSAGE);
        }
        try {
            DownloadScope scope = resolveScope(folderId);
            try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(output)) {
                zos.setUseZip64(Zip64Mode.AsNeeded);
                zos.setLevel(0);
                zos.setMethod(ZipArchiveEntry.DEFLATED);
                zos.setEncoding("UTF-8");
                Map<String, Integer> topLevelDedupe = new HashMap<>();
                int[] entryCount = {0};
                for (ShareRootScope rootScope : scope.roots()) {
                    if (!clientConnected.getAsBoolean()) {
                        return;
                    }
                    collectAndWrite(rootScope, zos, topLevelDedupe, entryCount, clientConnected);
                }
                zos.finish();
            }
        } finally {
            gate.exit();
        }
    }

    private void collectAndWrite(
            ShareRootScope rootScope,
            ZipArchiveOutputStream zos,
            Map<String, Integer> topLevelDedupe,
            int[] entryCount,
            BooleanSupplier clientConnected) throws IOException {
        Path downloadRoot = rootScope.downloadRoot();
        Path shareRoot = rootScope.shareRoot();
        String pathPrefix = rootScope.zipPathPrefix();
        if (Files.isRegularFile(downloadRoot, LinkOption.NOFOLLOW_LINKS)) {
            if (!shareRegistry.isSharedPathAllowed(downloadRoot)) {
                return;
            }
            if (!ShareExposeFilter.shouldExposeFromFolder(shareRoot, downloadRoot)) {
                return;
            }
            if (!SharePathSecurity.isInsideShareRoot(shareRoot, downloadRoot)) {
                return;
            }
            if (entryCount[0] >= maxEntries) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE, "This folder is too large to download as one zip");
            }
            String rel = downloadRoot.getFileName().toString();
            if (!pathPrefix.isEmpty()) {
                rel = ShareZipEntryPaths.sanitizeRelativePath(pathPrefix + "/" + rel);
            }
            rel = ShareZipEntryPaths.dedupeTopLevel(rel, topLevelDedupe);
            writeStoredEntry(zos, rel, downloadRoot);
            entryCount[0]++;
            return;
        }
        Set<Path> visitedDirs = new HashSet<>();
        Files.walkFileTree(downloadRoot, Set.of(), maxDepth, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!clientConnected.getAsBoolean()) {
                    return FileVisitResult.TERMINATE;
                }
                if (!SharePathSecurity.isInsideShareRoot(shareRoot, dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!ShareExposeFilter.shouldListDirectoryEntry(shareRoot, dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path dirReal;
                try {
                    dirReal = dir.toRealPath();
                } catch (IOException e) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!visitedDirs.add(dirReal)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!clientConnected.getAsBoolean()) {
                    return FileVisitResult.TERMINATE;
                }
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    return FileVisitResult.CONTINUE;
                }
                if (!ShareExposeFilter.shouldExposeFromFolder(shareRoot, file)) {
                    return FileVisitResult.CONTINUE;
                }
                if (!SharePathSecurity.isInsideShareRoot(shareRoot, file)) {
                    return FileVisitResult.CONTINUE;
                }
                if (!shareRegistry.isSharedPathAllowed(file)) {
                    return FileVisitResult.CONTINUE;
                }
                if (entryCount[0] >= maxEntries) {
                    throw new ResponseStatusException(
                            HttpStatus.PAYLOAD_TOO_LARGE, "This folder is too large to download as one zip");
                }
                String rel = ShareZipEntryPaths.fromRelativePath(downloadRoot, file);
                if (!pathPrefix.isEmpty()) {
                    rel = ShareZipEntryPaths.sanitizeRelativePath(pathPrefix + "/" + rel);
                }
                rel = ShareZipEntryPaths.dedupeTopLevel(rel, topLevelDedupe);
                writeStoredEntry(zos, rel, file);
                entryCount[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void writeStoredEntry(ZipArchiveOutputStream zos, String entryName, Path file) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
        entry.setMethod(ZipArchiveEntry.DEFLATED);
        entry.setSize(Files.size(file));
        zos.putArchiveEntry(entry);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] buf = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buf)) >= 0) {
                if (read > 0) {
                    zos.write(buf, 0, read);
                }
            }
        }
        zos.closeArchiveEntry();
    }

    private DownloadScope resolveScope(String folderId) throws IOException {
        if (folderId == null || folderId.isBlank()) {
            List<ShareRootScope> roots = new ArrayList<>();
            for (ShareEntry share : settingsStore.getSettings().getShares()) {
                Path root = Path.of(share.getPath()).toAbsolutePath().normalize();
                if (!Files.exists(root)) {
                    continue;
                }
                if (share.getKind() == ShareEntry.Kind.FOLDER && Files.isDirectory(root)) {
                    if (!SharePathSecurity.isInsideShareRoot(root, root)) {
                        continue;
                    }
                    roots.add(new ShareRootScope(
                            root,
                            root,
                            root.getFileName().toString()));
                } else if (share.getKind() == ShareEntry.Kind.FILE && Files.isRegularFile(root)) {
                    if (!shareRegistry.isSharedPathAllowed(root)) {
                        continue;
                    }
                    Path parent = root.getParent();
                    if (parent == null) {
                        continue;
                    }
                    roots.add(new ShareRootScope(root, parent, ""));
                }
            }
            if (roots.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nothing to download");
            }
            return new DownloadScope(TOP_LEVEL_ARCHIVE, roots);
        }
        ShareBrowseService.LocatedFolder located = shareBrowseService.locateFolder(folderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
        if (!ShareExposeFilter.shouldListDirectoryEntry(located.shareRoot(), located.directory())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found");
        }
        String name = ShareZipEntryPaths.archiveDownloadName(located.directory().getFileName().toString());
        return new DownloadScope(
                name,
                List.of(new ShareRootScope(located.directory(), located.shareRoot(), "")));
    }

    private record DownloadScope(String archiveName, List<ShareRootScope> roots) {
    }

    private record ShareRootScope(Path downloadRoot, Path shareRoot, String zipPathPrefix) {
    }
}
