package com.solocrew.crewcast.access;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIpResolverTest {

    @Test
    void cfHeaderFromIpv4LoopbackIsTrusted() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("Cf-Connecting-Ip", "203.0.113.10");
        assertEquals("203.0.113.10", ClientIpResolver.clientIp(req));
        assertTrue(ClientIpResolver.isTunnelRequest(req));
    }

    @Test
    void cfHeaderFromExpandedIpv6LoopbackIsTrusted() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("0:0:0:0:0:0:0:1");
        req.addHeader("Cf-Connecting-Ip", "198.51.100.9");
        assertEquals("198.51.100.9", ClientIpResolver.clientIp(req));
        assertTrue(ClientIpResolver.isTunnelRequest(req));
    }

    @Test
    void xffFromLoopbackUsesFirstHop() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.40, 10.0.0.1");
        assertEquals("203.0.113.40", ClientIpResolver.clientIp(req));
        assertFalse(ClientIpResolver.isTunnelRequest(req));
    }

    @Test
    void xffFromPublicRemoteIsIgnored() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.5");
        req.addHeader("X-Forwarded-For", "192.168.1.5");
        assertEquals("203.0.113.5", ClientIpResolver.clientIp(req));
        assertFalse(ClientIpResolver.isTunnelRequest(req));
    }

    @Test
    void cfHeaderFromLanRemoteIsIgnored() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.1.20");
        req.addHeader("Cf-Connecting-Ip", "192.168.1.5");
        assertEquals("192.168.1.20", ClientIpResolver.clientIp(req));
        assertFalse(ClientIpResolver.isTunnelRequest(req));
    }

    @Test
    void isLoopbackPeerRejectsHostnamesAndBlank() {
        assertFalse(ClientIpResolver.isLoopbackPeer("localhost"));
        assertFalse(ClientIpResolver.isLoopbackPeer(null));
        assertFalse(ClientIpResolver.isLoopbackPeer(""));
        assertTrue(ClientIpResolver.isLoopbackPeer("[::1]"));
    }
}
