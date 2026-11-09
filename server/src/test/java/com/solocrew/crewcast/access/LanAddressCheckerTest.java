package com.solocrew.crewcast.access;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanAddressCheckerTest {

    @Test
    void ipv4PrivateAndLoopbackAndLinkLocalAreLan() {
        assertTrue(LanAddressChecker.isPrivateLanAddress("10.1.2.3"));
        assertTrue(LanAddressChecker.isPrivateLanAddress("172.16.0.1"));
        assertTrue(LanAddressChecker.isPrivateLanAddress("192.168.1.5"));
        assertTrue(LanAddressChecker.isPrivateLanAddress("127.0.0.1"));
        assertTrue(LanAddressChecker.isPrivateLanAddress("169.254.1.1"));
    }

    @Test
    void ipv6LoopbackLinkLocalAndUlaAreLan() {
        assertTrue(LanAddressChecker.isPrivateLanAddress("::1"));
        assertTrue(LanAddressChecker.isPrivateLanAddress("fe80::1"));
        assertTrue(LanAddressChecker.isPrivateLanAddress("fd12::1"));
    }

    @Test
    void publicAndInvalidAreNotLan() {
        assertFalse(LanAddressChecker.isPrivateLanAddress("8.8.8.8"));
        assertFalse(LanAddressChecker.isPrivateLanAddress("2001:4860::8888"));
        assertFalse(LanAddressChecker.isPrivateLanAddress("172.32.0.1"));
        assertFalse(LanAddressChecker.isPrivateLanAddress("not-an-ip"));
        assertFalse(LanAddressChecker.isPrivateLanAddress(null));
        assertFalse(LanAddressChecker.isPrivateLanAddress(""));
    }
}
