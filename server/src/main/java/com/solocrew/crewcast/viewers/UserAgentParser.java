package com.solocrew.crewcast.viewers;

import java.util.Set;

public final class UserAgentParser {

    private static final String UNKNOWN_DEVICE = "Unknown device";
    private static final String VIEWER_FALLBACK = "Viewer";

    private static final Set<String> KNOWN_BROWSERS = Set.of(
            "Chrome", "Safari", "Firefox", "Edge", "Opera", "Samsung Internet");

    private UserAgentParser() {
    }

    public static String deviceLabel(String ua) {
        String safe = safeDeviceName(ua);
        return safe != null ? safe : UNKNOWN_DEVICE;
    }

    /**
     * Host-safe label for desktop display: only known device and browser names, never raw UA text.
     */
    public static String viewerLabel(String ua) {
        String device = safeDeviceName(ua);
        if (device == null) {
            device = VIEWER_FALLBACK;
        }
        String browser = safeBrowserName(ua);
        if (browser == null) {
            return device;
        }
        if (device.equalsIgnoreCase(browser) || device.toLowerCase().contains(browser.toLowerCase())) {
            return device;
        }
        return device + " · " + browser;
    }

    /** @return a known device name, or null if none matched (caller uses {@link #VIEWER_FALLBACK}). */
    static String safeDeviceName(String ua) {
        if (ua == null || ua.isBlank()) {
            return null;
        }
        String u = ua.toLowerCase();
        if (u.contains("iphone")) {
            return "iPhone";
        }
        if (u.contains("ipad")) {
            return "iPad";
        }
        if (u.contains("android") && u.contains("tv")) {
            return "Android TV";
        }
        if (u.contains("android")) {
            return "Android";
        }
        if (u.contains("smart-tv") || u.contains("smarttv")) {
            return "Smart TV";
        }
        if (u.contains("macintosh") || u.contains("mac os")) {
            return "Mac";
        }
        if (u.contains("windows")) {
            return "Windows PC";
        }
        if (u.contains("linux")) {
            return "Linux";
        }
        return null;
    }

    static String safeBrowserName(String ua) {
        if (ua == null || ua.isBlank()) {
            return null;
        }
        String u = ua.toLowerCase();
        if (u.contains("edg/")) {
            return "Edge";
        }
        if (u.contains("opr/") || u.contains("opera")) {
            return "Opera";
        }
        if (u.contains("samsungbrowser")) {
            return "Samsung Internet";
        }
        if (u.contains("crios")) {
            return "Chrome";
        }
        if (u.contains("fxios")) {
            return "Firefox";
        }
        if (u.contains("chrome") && !u.contains("chromium")) {
            return "Chrome";
        }
        if (u.contains("firefox")) {
            return "Firefox";
        }
        if (u.contains("safari") && !u.contains("chrome")) {
            return "Safari";
        }
        return null;
    }

    static boolean isKnownBrowser(String name) {
        return name != null && KNOWN_BROWSERS.contains(name);
    }
}
