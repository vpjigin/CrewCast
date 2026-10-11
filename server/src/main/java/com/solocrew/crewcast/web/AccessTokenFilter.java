package com.solocrew.crewcast.web;

import com.solocrew.crewcast.access.AccessSession;
import com.solocrew.crewcast.access.ClientIpResolver;
import com.solocrew.crewcast.access.LanAddressChecker;
import com.solocrew.crewcast.access.SharingState;
import com.solocrew.crewcast.settings.SettingsStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

public class AccessTokenFilter extends OncePerRequestFilter {

    private final AccessSession accessSession;
    private final SettingsStore settingsStore;
    private final SharingState sharingState;

    public AccessTokenFilter(AccessSession accessSession, SettingsStore settingsStore, SharingState sharingState) {
        this.accessSession = accessSession;
        this.settingsStore = settingsStore;
        this.sharingState = sharingState;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!sharingState.isSharingActive()) {
            AccessDenialLogger.log503SharingOff(request);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Sharing is off");
            return;
        }

        var settings = settingsStore.getSettings();
        String queryToken = trimToken(request.getParameter(AccessSession.QUERY_PARAM));
        String cookieToken = trimToken(cookieValue(request));

        if (accessSession.matches(queryToken) || accessSession.matches(cookieToken)) {
            setAccessCookie(request, response);
            filterChain.doFilter(request, response);
            return;
        }

        if (!settings.isRequirePrivateKey()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = ClientIpResolver.clientIp(request);
        boolean tunnel = ClientIpResolver.isTunnelRequest(request);
        if (settings.isAllowSameWifiAccess() && LanAddressChecker.isPrivateLanAddress(clientIp) && !tunnel) {
            filterChain.doFilter(request, response);
            return;
        }

        AccessDenialLogger.Reason reason = classifyDenial(queryToken, cookieToken, settings.isAllowSameWifiAccess(), clientIp, tunnel);
        AccessDenialLogger.log403(reason, request);
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
    }

    private static AccessDenialLogger.Reason classifyDenial(String queryToken, String cookieToken,
                                                            boolean allowSameWifi, String clientIp, boolean tunnel) {
        boolean anyToken = (queryToken != null && !queryToken.isBlank()) || (cookieToken != null && !cookieToken.isBlank());
        if (!anyToken) {
            if (!allowSameWifi && LanAddressChecker.isPrivateLanAddress(clientIp) && !tunnel) {
                return AccessDenialLogger.Reason.LAN_ONLY;
            }
            return AccessDenialLogger.Reason.MISSING_TOKEN;
        }
        return AccessDenialLogger.Reason.WRONG_TOKEN;
    }

    private void setAccessCookie(HttpServletRequest request, HttpServletResponse response) {
        boolean secure = request.isSecure()
                || ClientIpResolver.isTunnelRequest(request)
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        ResponseCookie cookie = ResponseCookie.from(AccessSession.COOKIE_NAME, accessSession.getToken())
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(7))
                .secure(secure)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String trimToken(String value) {
        return value != null ? value.trim() : null;
    }

    private static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (AccessSession.COOKIE_NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
