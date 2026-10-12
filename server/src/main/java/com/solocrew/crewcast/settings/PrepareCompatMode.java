package com.solocrew.crewcast.settings;

public enum PrepareCompatMode {
    OFF,
    NEEDS_COMPAT,
    ALL_VIDEOS;

    @Override
    public String toString() {
        return switch (this) {
            case OFF -> "Off";
            case NEEDS_COMPAT -> "Only files that need it";
            case ALL_VIDEOS -> "All videos";
        };
    }
}
