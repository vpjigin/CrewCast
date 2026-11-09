package com.solocrew.crewcast.access;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Resolves the viewer IP for access control and the viewer list.
 * Cloudflare Quick Tunnel (cloudflared) connects to the local server from loopback and
 * forwards {@code Cf-Connecting-Ip} / {@code X-Forwarded-For}. Those headers are trusted
 * only for loopback peers; otherwise a direct client could spoof them to look like a LAN
 * device (bypassing the private key) or to evade the host's viewer block list.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (isLoopbackPeer(remote)) {
            String cf = request.getHeader("Cf-Connecting-Ip");
            if (cf != null && !cf.isBlank()) {
                return cf.trim();
            }
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return remote;
    }

    public static boolean isTunnelRequest(HttpServletRequest request) {
        if (!isLoopbackPeer(request.getRemoteAddr())) {
            return false;
        }
        String cf = request.getHeader("Cf-Connecting-Ip");
        return cf != null && !cf.isBlank();
    }

    /**
     * True for IPv4 loopback (127.0.0.0/8) and IPv6 {@code ::1}, including forms like
     * {@code 0:0:0:0:0:0:0:1} and {@code [::1]}. Rejects hostnames so no DNS lookup occurs.
     */
    static boolean isLoopbackPeer(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        String s = remoteAddr.trim();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(isIpLiteralChar(c))) {
                return false;
            }
        }
        if (s.startsWith("[") && s.endsWith("]") && s.length() > 2) {
            s = s.substring(1, s.length() - 1);
        }
        try {
            return InetAddress.getByName(s).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean isIpLiteralChar(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F')
                || c == '.'
                || c == ':'
                || c == '%'
                || c == '['
                || c == ']';
    }
}
