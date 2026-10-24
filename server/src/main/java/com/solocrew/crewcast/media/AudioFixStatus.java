package com.solocrew.crewcast.media;

public record AudioFixStatus(
        AudioFixJobState status,
        int progress,
        String message,
        String cacheKey,
        Double speed,
        Integer etaSeconds,
        ConversionStartedBy startedBy,
        String startedByLabel,
        Integer queueAhead,
        String startedByClientId) {

    public AudioFixStatus(AudioFixJobState status, int progress, String message, String cacheKey) {
        this(status, progress, message, cacheKey, null, null, null, null, null, null);
    }
}
