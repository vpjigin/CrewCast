package com.solocrew.crewcast.access;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String clientIp(HttpServletRequest request) {
        String cf = request.getHeader("Cf-Connecting-Ip");
        if (cf != null && !cf.isBlank()) {
            return cf.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static boolean isTunnelRequest(HttpServletRequest request) {
        String cf = request.getHeader("Cf-Connecting-Ip");
        return cf != null && !cf.isBlank();
    }
}
