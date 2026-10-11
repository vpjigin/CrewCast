package com.solocrew.crewcast.viewers;

public record ViewerRecord(
        String key,
        String device,
        String fileName,
        boolean viaTunnel,
        long lastSeenMillis
) {
}
