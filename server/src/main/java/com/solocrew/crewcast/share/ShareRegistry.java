package com.solocrew.crewcast.share;

import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.SettingsStore;
import com.solocrew.crewcast.settings.ShareEntry;
import com.solocrew.crewcast.system.CrewcastLegacyMigration;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitOption;
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
        if (!isVideoFileName(abs.getFileName().toString())) {
            throw new IOException("Not a supported video file: " + abs.getFileName());
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
                try (Stream<Path> walk = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> isVideoFileName(p.getFileName().toString()))
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

    public synchronized boolean isPathAllowed(Path candidate) throws IOException {
        Path path = candidate.toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path) || !isVideoFileName(path.getFileName().toString())) {
            return false;
        }
        for (ShareEntry share : settingsStore.getSettings().getShares()) {
            Path root = Path.of(share.getPath()).toAbsolutePath().normalize();
            if (share.getKind() == ShareEntry.Kind.FILE) {
                if (path.equals(root)) {
                    return true;
                }
            } else if (path.startsWith(root)) {
                return true;
            }
        }
        return false;
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
        try (Stream<Path> walk = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> isVideoFileName(p.getFileName().toString()))
                    .forEach(p -> out.add(p.toAbsolutePath().normalize()));
        }
        return out;
    }
}
