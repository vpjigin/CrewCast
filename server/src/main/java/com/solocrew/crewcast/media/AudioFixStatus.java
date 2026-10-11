package com.solocrew.crewcast.media;

public record AudioFixStatus(
        AudioFixJobState status,
        int progress,
        String message,
        String cacheKey) {
}
