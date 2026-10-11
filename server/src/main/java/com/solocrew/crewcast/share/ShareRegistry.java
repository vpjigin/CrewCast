package com.solocrew.crewcast.share;

import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.settings.ShareEntry;
import com.solocrew.crewcast.system.CrewcastLegacyMigration;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class ShareRegistry {

    private static final HexFormat HEX = HexFormat.of();

    private final SettingsStore settingsStore;

    public ShareRegistry(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

    @PostConstruct
    public void init() {
        CrewcastLegacyMigration.migrateSettingsIfNeeded();
        settingsStore.load();
        syncFromSettings();
    }

    public synchronized void syncFromSettings() {
        // shares live in settings JSON; no in-memory duplicate list
    }

    public synchronized List<ShareEntry> listShareEntries() {
        return new ArrayList<>(settingsStore.getSettings().getShares());
    }

    public synchronized void setShareEntries(List<ShareEntry> entries) {
        AppSettings s = settingsStore.getSettings();
        s.setShares(new ArrayList<>(entries));
        settingsStore.save();
    }

    public synchronized void addFolder(Path folder) throws IOException {
        Path abs = folder.toAbsolutePath().normalize();
        if (!Files.isDirectory(abs)) {
            throw new IOException("Not a directory: " + abs);
        }
        addShare(new ShareEntry(abs.toString(), ShareEntry.Kind.FOLDER));
    }

    public synchronized void addFile(Path file) throws IOException {
        Path abs = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(abs)) {
            throw new IOException("Not a file: " + abs);
        }
        addShare(new ShareEntry(abs.toString(), ShareEntry.Kind.FILE));
    }

    public synchronized void removeShare(int index) {
        List<ShareEntry> shares = new ArrayList<>(settingsStore.getSettings().getShares());
        if (index >= 0 && index < shares.size()) {
            shares.remove(index);
            settingsStore.getSettings().setShares(shares);
            settingsStore.save();
        }
    }

    public synchronized void clearAllShares() {
        settingsStore.getSettings().setShares(new ArrayList<>());
        settingsStore.save();
    }

    private void addShare(ShareEntry entry) {
        List<ShareEntry> shares = new ArrayList<>(settingsStore.getSettings().getShares());
        String path = Path.of(entry.getPath()).toAbsolutePath().normalize().toString();
        entry.setPath(path);
        boolean exists = shares.stream().anyMatch(s ->
                Path.of(s.getPath()).toAbsolutePath().normalize().equals(Path.of(path)));
        if (!exists) {
            shares.add(entry);
            settingsStore.getSettings().setShares(shares);
            settingsStore.save();
        }
    }

    public synchronized List<VideoFile> listVideos() throws IOException {
        List<VideoFile> out = new ArrayList<>();
        for (ShareEntry share : settingsStore.getSettings().getShares()) {
            Path root = Path.of(share.getPath()).toAbsolutePath().normalize();
            if (!Files.exists(root)) {
                continue;
            }
            if (share.getKind() == ShareEntry.Kind.FILE) {
                if (Files.isRegularFile(root) && isVideoFileName(root.getFileName().toString())) {
                    out.add(toVideoFile(root));
                }
            } else if (Files.isDirectory(root)) {
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> isVideoFileName(p.getFileName().toString()))
                            .filter(p -> ShareExposeFilter.shouldExposeFromFolder(root, p))
                            .filter(this::isSharedPathAllowedUnchecked)
                            .forEach(p -> out.add(toVideoFile(p)));
                }
            }
        }
        out.sort(Comparator.comparing(VideoFile::displayName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public synchronized Optional<VideoFile> findById(String id) throws IOException {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return listVideos().stream().filter(v -> v.id().equals(id)).findFirst();
    }

    public synchronized Optional<Path> resolvePath(String id) throws IOException {
        return findById(id).map(VideoFile::path);
    }

    public synchronized List<SharedFile> listSharedFiles() throws IOException {
        List<SharedFile> out = new ArrayList<>();
        for (ShareEntry share : settingsStore.getSettings().getShares()) {
            Path root = Path.of(share.getPath()).toAbsolutePath().normalize();
            if (!Files.exists(root)) {
                continue;
            }
            if (share.getKind() == ShareEntry.Kind.FILE) {
                if (Files.isRegularFile(root) && isSharedPathAllowedUnchecked(root)) {
                    out.add(toSharedFile(root));
                }
            } else if (Files.isDirectory(root)) {
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> ShareExposeFilter.shouldExposeFromFolder(root, p))
                            .filter(this::isSharedPathAllowedUnchecked)
                            .forEach(p -> out.add(toSharedFile(p)));
                }
            }
        }
        out.sort(Comparator.comparing(SharedFile::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public synchronized Optional<SharedFile> findSharedFileById(String id) throws IOException {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return listSharedFiles().stream().filter(f -> f.id().equals(id)).findFirst();
    }

    public synchronized Optional<Path> resolveSharedPath(String id) throws IOException {
        return findSharedFileById(id).map(SharedFile::path);
    }

    public synchronized boolean isPathAllowed(Path candidate) throws IOException {
        Path path = candidate.toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path) || !isVideoFileName(path.getFileName().toString())) {
            return false;
        }
        return isSharedPathAllowed(path);
    }

    public synchronized boolean isSharedPathAllowed(Path candidate) {
        Path path = candidate.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            return false;
        }
        return isSharedPathAllowedUnchecked(path);
    }

    private boolean isSharedPathAllowedUnchecked(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        Path real;
        try {
            real = normalized.toRealPath();
        } catch (IOException e) {
            return false;
        }
        for (ShareEntry share : settingsStore.getSettings().getShares()) {
            Path root = Path.of(share.getPath()).toAbsolutePath().normalize();
            Path rootReal;
            try {
                rootReal = Files.exists(root) ? root.toRealPath() : root;
            } catch (IOException e) {
                continue;
            }
            if (share.getKind() == ShareEntry.Kind.FILE) {
                if (real.equals(rootReal)) {
                    return true;
                }
            } else if (real.startsWith(rootReal)) {
                return true;
            }
        }
        return false;
    }

    public synchronized String primaryShareTitle() {
        List<ShareEntry> shares = settingsStore.getSettings().getShares();
        if (shares.isEmpty()) {
            return "Shared files";
        }
        ShareEntry first = shares.getFirst();
        Path root = Path.of(first.getPath());
        if (first.getKind() == ShareEntry.Kind.FOLDER && Files.isDirectory(root)) {
            return root.getFileName().toString();
        }
        if (first.getKind() == ShareEntry.Kind.FILE && Files.isRegularFile(root)) {
            return root.getFileName().toString();
        }
        return root.getFileName() != null ? root.getFileName().toString() : "Shared files";
    }

    public static String idForPath(Path path) {
        try {
            Path real = path.toRealPath();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(real.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HEX.formatHex(digest);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private VideoFile toVideoFile(Path path) {
        return new VideoFile(idForPath(path), path.getFileName().toString(), path);
    }

    private SharedFile toSharedFile(Path path) {
        long size = 0;
        long modified = 0;
        try {
            size = Files.size(path);
            modified = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            // leave zeros
        }
        String name = path.getFileName().toString();
        return new SharedFile(idForPath(path), name, path, FileKind.forFileName(name), size, modified);
    }

    public static boolean isVideoFileName(String name) {
        return name.toLowerCase().matches(".*\\.(mp4|mkv|mov|webm|m4v)$");
    }

    public synchronized int countVideos() {
        try {
            return listVideos().size();
        } catch (IOException e) {
            return 0;
        }
    }

    public synchronized boolean hasShares() {
        return !settingsStore.getSettings().getShares().isEmpty();
    }

    public synchronized List<Path> listVideoPaths() throws IOException {
        return listVideos().stream().map(VideoFile::path).toList();
    }

    public synchronized List<Path> listVideosUnder(Path folderRoot) throws IOException {
        Path root = folderRoot.toAbsolutePath().normalize();
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> isVideoFileName(p.getFileName().toString()))
                    .filter(p -> ShareExposeFilter.shouldExposeFromFolder(root, p))
                    .filter(this::isSharedPathAllowedUnchecked)
                    .forEach(p -> out.add(p.toAbsolutePath().normalize()));
        }
        return out;
    }
}
