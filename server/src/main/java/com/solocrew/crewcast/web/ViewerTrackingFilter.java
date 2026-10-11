package com.solocrew.crewcast.web;

import com.solocrew.crewcast.access.ClientIpResolver;
import com.solocrew.crewcast.facade.SharingService;
import com.solocrew.crewcast.share.ShareRegistry;
import com.solocrew.crewcast.viewers.ViewerRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ViewerTrackingFilter extends OncePerRequestFilter {

    private final ShareRegistry shareRegistry;
    private final ViewerRegistry viewerRegistry;
    private final SharingService sharingService;

    public ViewerTrackingFilter(ShareRegistry shareRegistry, ViewerRegistry viewerRegistry,
                                SharingService sharingService) {
        this.shareRegistry = shareRegistry;
        this.viewerRegistry = viewerRegistry;
        this.sharingService = sharingService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/stream/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String id = request.getRequestURI().substring("/stream/".length());
        String ip = ClientIpResolver.clientIp(request);
        String ua = request.getHeader("User-Agent");
        if (viewerRegistry.isBlocked(ip, ua)) {
            AccessDenialLogger.log403(AccessDenialLogger.Reason.BLOCKED_VIEWER, request);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Removed by host");
            return;
        }

        String fileName = id;
        try {
            fileName = shareRegistry.findById(id)
                    .map(v -> v.displayName())
                    .orElse(id);
        } catch (IOException ignored) {
            // keep id as fallback label
        }

        viewerRegistry.recordView(ip, ua, fileName, ClientIpResolver.isTunnelRequest(request));
        sharingService.notifyViewersChanged();
        filterChain.doFilter(request, response);
    }
}
