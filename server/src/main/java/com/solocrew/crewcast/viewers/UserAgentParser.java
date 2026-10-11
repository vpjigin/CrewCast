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
}
