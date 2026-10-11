package com.solocrew.crewcast.support;

import com.solocrew.crewcast.system.CrewcastWriteGuard;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sets an isolated {@code -Dcrewcast.home} and refuses writes to the real {@code ~/.crewcast}.
 */
public final class CrewcastHomeIsolationExtension implements BeforeAllCallback {

    static volatile Path isolatedHome;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (isolatedHome != null) {
            return;
        }
        isolatedHome = Files.createTempDirectory("crewcast-test-home");
        System.setProperty("crewcast.home", isolatedHome.toString());
        CrewcastWriteGuard.setEnabled(true);
    }

    public static Path isolatedHome() {
        if (isolatedHome == null) {
            throw new IllegalStateException("CrewcastHomeIsolationExtension not registered");
        }
        return isolatedHome;
    }
}
