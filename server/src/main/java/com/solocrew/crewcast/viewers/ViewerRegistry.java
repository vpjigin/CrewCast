package com.solocrew.crewcast.viewers;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ViewerRegistry {

    private static final long WATCHING_WINDOW_MS = 30_000;

    private final Map<String, ViewerRecord> viewers = new ConcurrentHashMap<>();
    private final Set<String> blocked = ConcurrentHashMap.newKeySet();

    public void recordView(String ip, String userAgent, String fileName, boolean viaTunnel) {
        String key = key(ip, userAgent);
        if (blocked.contains(key)) {
            return;
        }
        viewers.put(key, new ViewerRecord(
                key,
                UserAgentParser.deviceLabel(userAgent),
                fileName,
                viaTunnel,
                System.currentTimeMillis()));
    }

    public boolean isBlocked(String ip, String userAgent) {
        return blocked.contains(key(ip, userAgent));
    }

    public void block(String key) {
        blocked.add(key);
        viewers.remove(key);
    }

    public void clearOnSharingRestart() {
        blocked.clear();
        viewers.clear();
    }

    public List<ViewerRecord> activeViewers() {
        long now = System.currentTimeMillis();
        List<ViewerRecord> out = new ArrayList<>();
        for (ViewerRecord v : viewers.values()) {
            if (now - v.lastSeenMillis() <= WATCHING_WINDOW_MS) {
                out.add(v);
            }
        }
        out.sort((a, b) -> Long.compare(b.lastSeenMillis(), a.lastSeenMillis()));
        return out;
    }

    public int activeViewerCount() {
        return activeViewers().size();
    }

    private static String key(String ip, String userAgent) {
        return ip + "|" + (userAgent != null ? userAgent : "");
    }
}
