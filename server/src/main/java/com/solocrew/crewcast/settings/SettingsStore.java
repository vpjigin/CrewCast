package com.solocrew.crewcast.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.solocrew.crewcast.system.CrewcastPaths;
import com.solocrew.crewcast.system.CrewcastWriteGuard;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Component
public class SettingsStore {

    private Path settingsPath() {
        return CrewcastPaths.settingsFile();
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private AppSettings settings = new AppSettings();
    private boolean settingsExistedBeforeLoad;

    public synchronized AppSettings getSettings() {
        return settings;
    }

    public synchronized void load() {
        Path path = settingsPath();
        settingsExistedBeforeLoad = Files.exists(path);
        try {
            if (settingsExistedBeforeLoad) {
                settings = mapper.readValue(path.toFile(), AppSettings.class);
                if (settings.getShares() == null) {
                    settings.setShares(new java.util.ArrayList<>());
                }
            }
        } catch (IOException e) {
            settings = new AppSettings();
        }
    }

    public synchronized void save() {
        try {
            Path path = settingsPath();
            CrewcastWriteGuard.check(path);
            Files.createDirectories(path.getParent());
            mapper.writeValue(path.toFile(), settings);
            restrictSettingsPermissions(path);
        } catch (IOException ignored) {
        }
    }

    private static void restrictSettingsPermissions(Path file) {
        if (!isWindows()) {
            try {
                Set<PosixFilePermission> perms = EnumSet.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(file, perms);
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public synchronized void update(AppSettings newSettings) {
        this.settings = newSettings;
        save();
    }

    public Path settingsFilePath() {
        return settingsPath();
    }

    public boolean settingsFileExistedBeforeLoad() {
        return settingsExistedBeforeLoad;
    }
}
