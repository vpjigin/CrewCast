package com.solocrew.crewcast.system;

import java.nio.file.Path;

public final class CrewcastPaths {

    private CrewcastPaths() {
    }

    public static Path home() {
        String prop = System.getProperty("crewcast.home");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".crewcast").toAbsolutePath().normalize();
    }

    public static Path settingsFile() {
        return home().resolve("settings.json");
    }

    public static Path cacheDir() {
        return home().resolve("cache");
    }

    public static Path binDir() {
        return home().resolve("bin");
    }
}
