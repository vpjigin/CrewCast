package com.solocrew.crewcast.system;

import java.nio.file.Path;

/** Blocks writes to the real {@code ~/.crewcast} during tests when the guard is enabled. */
public final class CrewcastWriteGuard {

    private static volatile boolean enabled;

    private CrewcastWriteGuard() {
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Real user data directory ({@code $HOME/.crewcast}), not affected by test overrides of {@code user.home}. */
    public static Path realDefaultHome() {
        String home = System.getenv("HOME");
        if (home == null || home.isBlank()) {
            home = System.getProperty("user.home");
        }
        return Path.of(home, ".crewcast").toAbsolutePath().normalize();
    }

    public static void check(Path target) {
        if (!enabled) {
            return;
        }
        Path normalized = target.toAbsolutePath().normalize();
        Path realHome = realDefaultHome();
        if (normalized.startsWith(realHome)) {
            throw new IllegalStateException(
                    "Refusing write to real Crewcast home during tests: " + normalized
                            + " (set -Dcrewcast.home to an isolated directory)");
        }
    }
}
