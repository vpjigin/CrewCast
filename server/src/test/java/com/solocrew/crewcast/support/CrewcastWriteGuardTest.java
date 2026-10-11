package com.solocrew.crewcast.support;

import com.solocrew.crewcast.system.CrewcastWriteGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrewcastWriteGuardTest extends CrewcastSpringTestBase {

    @AfterEach
    void disableGuardIfNeeded() {
        CrewcastWriteGuard.setEnabled(true);
    }

    @Test
    void blocksWriteToRealDefaultHomeWhenGuardEnabled() {
        CrewcastWriteGuard.setEnabled(true);
        Path realSettings = CrewcastWriteGuard.realDefaultHome().resolve("settings.json");
        assertThrows(IllegalStateException.class, () -> CrewcastWriteGuard.check(realSettings));
    }

    @Test
    void allowsWriteUnderIsolatedTestHome() {
        CrewcastWriteGuard.setEnabled(true);
        Path testSettings = CrewcastSpringTestBase.isolatedHome().resolve("settings.json");
        assertDoesNotThrow(() -> CrewcastWriteGuard.check(testSettings));
    }
}
