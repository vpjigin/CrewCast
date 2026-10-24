package com.solocrew.crewcast.viewers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UserAgentParserTest {

    @Test
    void viewerLabelNeverEchoesRawUa() {
        String malicious = "<html><img src=http://x>";
        String label = UserAgentParser.viewerLabel(malicious);
        assertFalse(label.contains("<"));
        assertFalse(label.contains("http"));
        assertEquals("Viewer", label);
    }

    @Test
    void viewerLabelCombinesKnownDeviceAndBrowser() {
        String ua = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile";
        assertEquals("Android · Chrome", UserAgentParser.viewerLabel(ua));
    }

    @Test
    void deviceLabelDoesNotEchoRawUa() {
        String ua = "<script>alert(1)</script>";
        assertEquals("Unknown device", UserAgentParser.deviceLabel(ua));
    }
}
