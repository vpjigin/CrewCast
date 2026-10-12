package com.solocrew.crewcast.share;

import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.settings.ShareEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
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
import java.util.Set;
import java.util.function.BooleanSupplier;

@Service
public class ShareZipDownloadService {

    private static final Logger log = LoggerFactory.getLogger(ShareZipDownloadService.class);

    public static final String TOO_MANY_MESSAGE =
            "Too many download-all requests are in progress. Try again in a moment.";
    public static final String TOP_LEVEL_ARCHIVE = "Crewcast-shared.zip";

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

    public record PreparedDownload(
            String archiveName,
            List<StreamingStoredZipWriter.PlannedEntry> entries,
            long contentLength) {
    }

    public PreparedDownload prepareDownload(String folderId) throws IOException {
        DownloadScope scope = resolveScope(folderId);
        List<StreamingStoredZipWriter.PlannedEntry> plans = planEntries(scope);
        return new PreparedDownload(
                scope.archiveName(),
                List.copyOf(plans),
                StreamingStoredZipWriter.totalArchiveSize(plans));
    }

    public String archiveFilenameFor(String folderId) throws IOException {
        return prepareDownload(folderId).archiveName();
    }

    public long zipContentLength(String folderId) throws IOException {
        return prepareDownload(folderId).contentLength();
    }

    public void writeZip(PreparedDownload prepared, OutputStream output, BooleanSupplier clientConnected)
            throws IOException {
        if (!gate.tryEnter()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_MESSAGE);
        }
        try {
            try {
                StreamingStoredZipWriter.writeArchive(output, prepared.entries(), clientConnected);
            } catch (IOException e) {
                log.warn("Zip download aborted: {}", e.getMessage());
                throw e;
            }
        } finally {
            gate.exit();
        }
    }

    List<StreamingStoredZipWriter.PlannedEntry> planEntries(DownloadScope scope) throws IOException {
        List<StreamingStoredZipWriter.PlannedEntry> plans = new ArrayList<>();
        Map<String, Integer> topLevelDedupe = new HashMap<>();
        for (ShareRootScope rootScope : scope.roots()) {
            collectPlanned(rootScope, plans, topLevelDedupe);
        }
        return plans;
    }

    private void collectPlanned(
            ShareRootScope rootScope,
            List<StreamingStoredZipWriter.PlannedEntry> plans,
            Map<String, Integer> topLevelDedupe) throws IOException {
        Path downloadRoot = rootScope.downloadRoot();
        Path shareRoot = rootScope.shareRoot();
        String pathPrefix = rootScope.zipPathPrefix();
        if (Files.isRegularFile(downloadRoot, LinkOption.NOFOLLOW_LINKS)) {
            addFilePlan(downloadRoot, shareRoot, pathPrefix, "", plans, topLevelDedupe);
            return;
        }
        Set<Path> visitedDirs = new HashSet<>();
        Files.walkFileTree(downloadRoot, Set.of(), maxDepth, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
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
                if (plans.size() >= maxEntries) {
                    throw new ResponseStatusException(
                            HttpStatus.PAYLOAD_TOO_LARGE, "This folder is too large to download as one zip");
                }
                String rel = ShareZipEntryPaths.fromRelativePath(downloadRoot, file);
                addFilePlan(file, shareRoot, pathPrefix, rel, plans, topLevelDedupe);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void addFilePlan(
            Path file,
            Path shareRoot,
            String pathPrefix,
            String relativePath,
            List<StreamingStoredZipWriter.PlannedEntry> plans,
            Map<String, Integer> topLevelDedupe) throws IOException {
        if (!shareRegistry.isSharedPathAllowed(file)) {
            return;
        }
        if (!ShareExposeFilter.shouldExposeFromFolder(shareRoot, file)) {
            return;
        }
        if (!SharePathSecurity.isInsideShareRoot(shareRoot, file)) {
            return;
        }
        if (plans.size() >= maxEntries) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "This folder is too large to download as one zip");
        }
        String rel = relativePath;
        if (rel.isEmpty()) {
            rel = file.getFileName().toString();
        }
        if (!pathPrefix.isEmpty()) {
            rel = ShareZipEntryPaths.sanitizeRelativePath(pathPrefix + "/" + rel);
        }
        rel = ShareZipEntryPaths.dedupeTopLevel(rel, topLevelDedupe);
        long size = Files.size(file);
        plans.add(StreamingStoredZipWriter.PlannedEntry.of(rel, file, size));
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
