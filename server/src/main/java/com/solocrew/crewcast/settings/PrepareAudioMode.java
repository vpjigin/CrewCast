package com.solocrew.crewcast.settings;

public enum PrepareAudioMode {
    OFF,
    DEFAULT_TRACK,
    ALL_TRACKS;

    @Override
    public String toString() {
        return switch (this) {
            case OFF -> "Off";
            case DEFAULT_TRACK -> "Default track only";
            case ALL_TRACKS -> "All tracks";
        };
    }
}
