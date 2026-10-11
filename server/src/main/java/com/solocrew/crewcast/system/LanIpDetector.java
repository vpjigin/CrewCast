package com.solocrew.crewcast.system;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public final class LanIpDetector {

    private LanIpDetector() {
    }

    public static String primaryLanIp() {
        List<String> ips = allLanIps();
        return ips.isEmpty() ? "127.0.0.1" : ips.getFirst();
    }

    public static List<String> allLanIps() {
        List<String> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return out;
            }
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (ni == null || !ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                String name = ni.getName().toLowerCase();
                if (name.startsWith("utun") || name.startsWith("awdl") || name.contains("docker")
                        || name.contains("vmnet") || name.contains("vbox")) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                for (InetAddress addr : Collections.list(addrs)) {
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
                        continue;
                    }
                    if (addr.isSiteLocalAddress()) {
                        out.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
