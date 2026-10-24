package com.solocrew.crewcast.viewers;

public final class UserAgentParser {

    private UserAgentParser() {
    }

    public static String deviceLabel(String ua) {
        if (ua == null || ua.isBlank()) {
            return "Unknown device";
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
        if (u.contains("chrome")) {
            return "Chrome browser";
        }
        if (u.contains("safari")) {
            return "Safari browser";
        }
        if (u.contains("firefox")) {
            return "Firefox browser";
        }
        return ua.length() > 48 ? ua.substring(0, 45) + "…" : ua;
    }

    /** Device plus browser, e.g. {@code Android · Chrome}. */
    public static String viewerLabel(String ua) {
        String device = deviceLabel(ua);
        String browser = browserLabel(ua);
        if (browser == null || browser.isBlank()) {
            return device;
        }
        if (device.toLowerCase().contains(browser.toLowerCase())) {
            return device;
        }
        return device + " · " + browser;
    }

    static String browserLabel(String ua) {
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
}
