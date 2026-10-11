package com.solocrew.crewcast.media;

import java.nio.file.Path;

/** One shared media fixture per JVM so parallel Surefire classes do not run ffmpeg concurrently. */
final class MediaTestFixtures {

    private static Path coverArt;
    private static Path h264;

    private MediaTestFixtures() {
    }

    static Path coverArt() throws Exception {
        synchronized (MediaTestFixtures.class) {
            if (coverArt == null) {
                coverArt = CoverArtFixture.create();
            }
            return coverArt;
        }
    }

    static Path h264() throws Exception {
        synchronized (MediaTestFixtures.class) {
            if (h264 == null) {
                h264 = H264Fixture.create();
            }
            return h264;
        }
    }
}
