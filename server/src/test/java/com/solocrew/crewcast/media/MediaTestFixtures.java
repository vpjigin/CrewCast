package com.solocrew.crewcast.media;

import java.nio.file.Path;

/** One shared media fixture per JVM so parallel Surefire classes do not run ffmpeg concurrently. */
final class MediaTestFixtures {

    private static Path coverArt;
    private static Path h264;
    private static Path silentH264;

    private MediaTestFixtures() {
    }

    static Path coverArt() throws Exception {
        if (!FfmpegTestSupport.shouldBuildFfmpegFixtures()) {
            throw new IllegalStateException(FfmpegTestSupport.FFMPEG_NOT_ON_PATH);
        }
        synchronized (MediaTestFixtures.class) {
            if (coverArt == null) {
                coverArt = CoverArtFixture.create();
            }
            return coverArt;
        }
    }

    static Path h264() throws Exception {
        if (!FfmpegTestSupport.shouldBuildFfmpegFixtures()) {
            throw new IllegalStateException(FfmpegTestSupport.FFMPEG_NOT_ON_PATH);
        }
        synchronized (MediaTestFixtures.class) {
            if (h264 == null) {
                h264 = H264Fixture.create();
            }
            return h264;
        }
    }

    static Path silentH264() throws Exception {
        if (!FfmpegTestSupport.shouldBuildFfmpegFixtures()) {
            throw new IllegalStateException(FfmpegTestSupport.FFMPEG_NOT_ON_PATH);
        }
        synchronized (MediaTestFixtures.class) {
            if (silentH264 == null) {
                silentH264 = H264Fixture.createSilent();
            }
            return silentH264;
        }
    }
}
