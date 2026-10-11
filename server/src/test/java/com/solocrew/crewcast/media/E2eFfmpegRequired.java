package com.solocrew.crewcast.media;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks integration tests that need ffmpeg on PATH and/or Playwright browsers. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("e2e")
@EnabledIf(
        value = "com.solocrew.crewcast.media.FfmpegTestSupport#ffmpegAndFfprobeAvailable",
        disabledReason = FfmpegTestSupport.FFMPEG_NOT_ON_PATH)
public @interface E2eFfmpegRequired {
}
