package com.solocrew.crewcast.share;

import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.settings.ShareEntry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class ShareBrowseService {

    private final SettingsStore settingsStore;
    private final ShareRegistry shareRegistry;

    public ShareBrowseService(SettingsStore settingsStore, ShareRegistry shareRegistry) {
        this.settingsStore = settingsStore;
        this.shareRegistry = shareRegistry;
    }

    public BrowseResponse browse(String folderId) throws IOException {
        if (folderId == null || folderId.isBlank()) {
            return topLevel();
        }
        LocatedFolder located = locateFolder(folderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
        if (!ShareExposeFilter.shouldListDirectoryEntry(located.shareRoot(), located.directory())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found");
        }
        return folderPage(located);
    }

    private BrowseResponse topLevel() throws IOException {
        List<BrowseFolderItem> folders = new ArrayList<>();
        List<BrowseFileItem> files = new ArrayList<>();
        for (ShareEntry share : settingsStore.getSettings().getShares()) {
            Path root = Path.of(share.getPath()).toAbsolutePath().normalize();
            if (!Files.exists(root)) {
                continue;
            }
            if (share.getKind() == ShareEntry.Kind.FOLDER && Files.isDirectory(root)) {
                if (!SharePathSecurity.isInsideShareRoot(root, root)) {
                    continue;
                }
                folders.add(new BrowseFolderItem(
                        ShareRegistry.idForPath(root),
                        root.getFileName().toString(),
                        countImmediateChildren(root, root)));
            } else if (share.getKind() == ShareEntry.Kind.FILE && Files.isRegularFile(root)) {
                if (shareRegistry.isSharedPathAllowed(root)) {
                    files.add(toFileItem(root));
                }
            }
        }
        folders.sort(folderComparator());
        files.sort(fileComparator());
        int folderCount = folders.size();
        int fileCount = files.size();
        return new BrowseResponse(
                "Shared",
                headerSummary(folderCount, fileCount),
                List.of(new BrowseBreadcrumbItem("", "Shared")),
                folders,
                files,
                folderCount,
                fileCount);
    }

    private BrowseResponse folderPage(LocatedFolder located) throws IOException {
        Path dir = located.directory();
        Path shareRoot = located.shareRoot();
        List<BrowseFolderItem> folders = new ArrayList<>();
        List<BrowseFileItem> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> entries = stream
                    .filter(p -> ShareExposeFilter.shouldListEntry(shareRoot, p))
                    .sorted(entryComparator())
                    .toList();
            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    folders.add(new BrowseFolderItem(
                            ShareRegistry.idForPath(entry),
                            entry.getFileName().toString(),
                            countImmediateChildren(shareRoot, entry)));
                } else if (Files.isRegularFile(entry)) {
                    files.add(toFileItem(entry));
                }
            }
        }
        return new BrowseResponse(
                dir.getFileName().toString(),
                null,
                buildBreadcrumb(shareRoot, dir),
                folders,
                files,
                0,
                0);
    }

    private List<BrowseBreadcrumbItem> buildBreadcrumb(Path shareRoot, Path current) {
        List<BrowseBreadcrumbItem> out = new ArrayList<>();
        out.add(new BrowseBreadcrumbItem("", "Shared"));
        Path normalized = current.toAbsolutePath().normalize();
        out.add(new BrowseBreadcrumbItem(ShareRegistry.idForPath(shareRoot), shareRoot.getFileName().toString()));
        Path rel = shareRoot.relativize(normalized);
        Path walk = shareRoot;
        for (int i = 0; i < rel.getNameCount(); i++) {
            walk = walk.resolve(rel.getName(i));
            out.add(new BrowseBreadcrumbItem(
                    ShareRegistry.idForPath(walk),
                    rel.getName(i).toString()));
        }
        return out;
    }

    private Optional<LocatedFolder> locateFolder(String folderId) throws IOException {
        for (ShareEntry share : settingsStore.getSettings().getShares()) {
            if (share.getKind() != ShareEntry.Kind.FOLDER) {
                continue;
            }
            Path root = Path.of(share.getPath()).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                continue;
            }
            if (ShareRegistry.idForPath(root).equals(folderId)) {
                if (SharePathSecurity.isInsideShareRoot(root, root)) {
                    return Optional.of(new LocatedFolder(root, root));
                }
                return Optional.empty();
            }
            Optional<Path> nested = findDirectoryById(root, root, folderId, new HashSet<>());
            if (nested.isPresent()) {
                Path dir = nested.get();
                if (ShareExposeFilter.shouldListDirectoryEntry(root, dir)) {
                    return Optional.of(new LocatedFolder(dir, root));
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<Path> findDirectoryById(
            Path shareRoot, Path dir, String folderId, Set<Path> visitedRealDirs) throws IOException {
        Path dirReal;
        try {
            dirReal = dir.toRealPath();
        } catch (IOException e) {
            return Optional.empty();
        }
        if (!visitedRealDirs.add(dirReal)) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path child : stream.toList()) {
                if (!Files.isDirectory(child) || !ShareExposeFilter.shouldListDirectoryEntry(shareRoot, child)) {
                    continue;
                }
                if (ShareRegistry.idForPath(child).equals(folderId)) {
                    return Optional.of(child);
                }
                Optional<Path> deep = findDirectoryById(shareRoot, child, folderId, visitedRealDirs);
                if (deep.isPresent()) {
                    return deep;
                }
            }
        }
        return Optional.empty();
    }

    private static int countImmediateChildren(Path shareRoot, Path dir) throws IOException {
        int count = 0;
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path entry : stream.toList()) {
                if (ShareExposeFilter.shouldListEntry(shareRoot, entry)) {
                    count++;
                }
            }
        }
        return count;
    }

    private BrowseFileItem toFileItem(Path path) {
        long size = 0;
        long modified = 0;
        try {
            size = Files.size(path);
            modified = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            // leave zeros
        }
        String name = path.getFileName().toString();
        FileKind kind = FileKind.forFileName(name);
        return new BrowseFileItem(
                ShareRegistry.idForPath(path),
                name,
                kind.name(),
                kind.label(),
                FileKind.extensionLabel(name),
                size,
                modified,
                kind.viewableInBrowser());
    }

    private static String headerSummary(int folders, int files) {
        StringBuilder sb = new StringBuilder();
        if (folders > 0) {
            sb.append(folders).append(folders == 1 ? " folder" : " folders");
        }
        if (files > 0) {
            if (!sb.isEmpty()) {
                sb.append(" · ");
            }
            sb.append(files).append(files == 1 ? " file" : " files");
        }
        if (sb.isEmpty()) {
            return "Nothing shared";
        }
        return sb.append(" shared").toString();
    }

    private static Comparator<BrowseFolderItem> folderComparator() {
        return Comparator.comparing(BrowseFolderItem::name, NaturalOrder.stringsIgnoreCase());
    }

    private static Comparator<BrowseFileItem> fileComparator() {
        return Comparator.comparing(BrowseFileItem::name, NaturalOrder.stringsIgnoreCase());
    }

    private static Comparator<Path> entryComparator() {
        return (a, b) -> {
            boolean aDir = Files.isDirectory(a);
            boolean bDir = Files.isDirectory(b);
            if (aDir != bDir) {
                return aDir ? -1 : 1;
            }
            return NaturalOrder.compareIgnoreCase(a.getFileName().toString(), b.getFileName().toString());
        };
    }

    private record LocatedFolder(Path directory, Path shareRoot) {
    }

    public record BrowseResponse(
            String title,
            String headerSummary,
            List<BrowseBreadcrumbItem> breadcrumb,
            List<BrowseFolderItem> folders,
            List<BrowseFileItem> files,
            int topLevelFolderCount,
            int topLevelFileCount) {
    }

    public record BrowseBreadcrumbItem(String id, String name) {
    }

    public record BrowseFolderItem(String id, String name, int itemCount) {
    }

    public record BrowseFileItem(
            String id,
            String name,
            String kindKey,
            String kind,
            String ext,
            long sizeBytes,
            long modifiedMillis,
            boolean viewable) {
    }
}
