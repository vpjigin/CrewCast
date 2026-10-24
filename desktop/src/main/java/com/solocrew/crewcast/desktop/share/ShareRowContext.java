package com.solocrew.crewcast.desktop.share;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

public record ShareRowContext(
        Supplier<Boolean> encoderAvailable,
        Function<Path, Integer> estimateCompatMinutes,
        ShareRowActions actions) {}
