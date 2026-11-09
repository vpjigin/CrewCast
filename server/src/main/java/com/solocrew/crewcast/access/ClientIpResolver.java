package com.solocrew.crewcast.access;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

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

    private static final Pattern IPV4_DOTTED = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    /**
     * True for IPv4 loopback (127.0.0.0/8) and IPv6 {@code ::1}, including forms like
     * {@code 0:0:0:0:0:0:0:1} and {@code [::1]}. Rejects hostnames so no DNS lookup occurs.
     */
    static boolean isLoopbackPeer(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        String s = remoteAddr.trim();
        if (s.startsWith("[") && s.endsWith("]") && s.length() > 2) {
            s = s.substring(1, s.length() - 1);
        }
        if (!looksLikeIpLiteral(s)) {
            return false;
        }
        try {
            return InetAddress.getByName(s).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * Accepts IPv4 dotted decimal or IPv6-shaped literals only, so
     * {@link InetAddress#getByName(String)} never performs a DNS lookup.
     */
    private static boolean looksLikeIpLiteral(String s) {
        if (IPV4_DOTTED.matcher(s).matches()) {
            return true;
        }
        if (!s.contains(":")) {
            return false;
        }
        int pct = s.indexOf('%');
        String addr = pct < 0 ? s : s.substring(0, pct);
        String scope = pct < 0 ? null : s.substring(pct + 1);
        for (int i = 0; i < addr.length(); i++) {
            if (!isIpv6AddrChar(addr.charAt(i))) {
                return false;
            }
        }
        if (scope != null) {
            if (scope.isEmpty()) {
                return false;
            }
            for (int i = 0; i < scope.length(); i++) {
                if (!isScopeChar(scope.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isIpv6AddrChar(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F')
                || c == ':'
                || c == '.';
    }

    private static boolean isScopeChar(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '_'
                || c == '-'
                || c == '.';
    }
}
