package com.solocrew.crewcast.support;

import com.solocrew.crewcast.system.CrewcastWriteGuard;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Ensures {@code crewcast.home} is isolated before the Spring context starts. */
public abstract class CrewcastSpringTestBase {

    static final Path ISOLATED_HOME;

    static {
        try {
            ISOLATED_HOME = Files.createTempDirectory("crewcast-test-home");
            System.setProperty("crewcast.home", ISOLATED_HOME.toString());
            CrewcastWriteGuard.setEnabled(true);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void crewcastHome(DynamicPropertyRegistry registry) {
        registry.add("crewcast.home", () -> ISOLATED_HOME.toString());
    }

    public static Path isolatedHome() {
        return ISOLATED_HOME;
    }
}
