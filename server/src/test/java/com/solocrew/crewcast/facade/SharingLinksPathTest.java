package com.solocrew.crewcast.facade;

import com.solocrew.crewcast.settings.AppSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharingLinksPathTest {

    @Test
    void linkPathIncludesTokenOnlyWhenKeyRequired() {
        assertEquals("/?t=abc", SharingService.linkPath(true, "abc"));
        assertEquals("/", SharingService.linkPath(false, "abc"));
    }

    @Test
    void appSettingsDefaultsForAccessToggles() {
        AppSettings settings = new AppSettings();
        assertTrue(settings.isRequirePrivateKey());
        assertFalse(settings.isAllowSameWifiAccess());
    }
}
