package com.solocrew.crewcast.desktop.share;

import com.solocrew.crewcast.media.CompatUiState;
import com.solocrew.crewcast.media.ConversionStartedBy;

import java.nio.file.Path;

public record CompatRowModel(
        CompatUiState state,
        int progress,
        String tooltip,
        int queueAhead,
        Double speed,
        Integer etaSeconds,
        ConversionStartedBy startedBy,
        String startedByLabel,
        Path videoPath) {}
