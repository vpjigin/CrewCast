package com.solocrew.crewcast.media;

public final class ConversionLimits {

    public static final int MAX_VIEWER_QUEUED_JOBS = 3;

    /** Shown when a viewer tries to queue a 4th job while 3 are already waiting (RUNNING does not count). */
    public static final String HOST_BUSY_MESSAGE =
            "The host's laptop is busy – too many conversions are waiting. Try again after one finishes.";

    private ConversionLimits() {
    }
}
