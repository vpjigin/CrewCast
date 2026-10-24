package com.solocrew.crewcast.media;

public final class InvalidAudioTrackException extends RuntimeException {

    private final int requestedIndex;

    public InvalidAudioTrackException(int requestedIndex) {
        super("Audio track " + requestedIndex + " does not exist for this file");
        this.requestedIndex = requestedIndex;
    }

    public int requestedIndex() {
        return requestedIndex;
    }
}
