package com.solocrew.crewcast.access;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class LanAddressChecker {

    private LanAddressChecker() {
    }

    public static boolean isPrivateLanAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (!(addr instanceof Inet4Address)) {
                return addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
            }
            byte[] b = addr.getAddress();
            int b0 = b[0] & 0xff;
            int b1 = b[1] & 0xff;
            if (b0 == 10) {
                return true;
            }
            if (b0 == 172 && b1 >= 16 && b1 <= 31) {
                return true;
            }
            if (b0 == 192 && b1 == 168) {
                return true;
            }
            return addr.isLoopbackAddress() || addr.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
