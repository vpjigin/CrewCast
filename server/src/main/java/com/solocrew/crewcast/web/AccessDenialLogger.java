package com.solocrew.crewcast.web;

import com.solocrew.crewcast.access.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AccessDenialLogger {

    private static final Logger LOG = LoggerFactory.getLogger(AccessDenialLogger.class);

    public enum Reason {
        MISSING_TOKEN,
        WRONG_TOKEN,
        BLOCKED_VIEWER,
        LAN_ONLY,
        SHARING_OFF,
        OTHER
    }

    private AccessDenialLogger() {
    }

    public static void log403(Reason reason, HttpServletRequest request) {
        log403(reason, request, null);
    }

    public static void log403(Reason reason, HttpServletRequest request, String detail) {
        if (LOG.isInfoEnabled()) {
            if (detail != null && !detail.isBlank()) {
                LOG.info("HTTP 403 reason={} path={} detail={}", reason, request.getRequestURI(), detail);
            } else {
                LOG.info("HTTP 403 reason={} path={}", reason, request.getRequestURI());
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "HTTP 403 context path={} remoteAddr={} cfConnectingIp={} xForwardedFor={} userAgent={}",
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    nullToDash(request.getHeader("Cf-Connecting-Ip")),
                    nullToDash(request.getHeader("X-Forwarded-For")),
                    nullToDash(request.getHeader("User-Agent")));
        }
    }

    public static void log503SharingOff(HttpServletRequest request) {
        if (LOG.isInfoEnabled()) {
            LOG.info("HTTP 503 sharing off path={}", request.getRequestURI());
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "HTTP 503 sharing off path={} resolvedClientIp={} cfConnectingIp={}",
                    request.getRequestURI(),
                    ClientIpResolver.clientIp(request),
                    nullToDash(request.getHeader("Cf-Connecting-Ip")));
        }
    }

    private static String nullToDash(String v) {
        return v == null || v.isBlank() ? "-" : v.trim();
    }
}
