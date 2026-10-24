package com.solocrew.crewcast.facade;

import com.solocrew.crewcast.media.CompatUiState;

import java.nio.file.Path;

public record ShareVideoCompatRow(
        Path path,
        String displayName,
        CompatUiState state,
        int progress,
        String tooltip,
        boolean canPrepare,
        boolean canCancel,
        int queueAhead,
        Double speed,
        Integer etaSeconds,
        com.solocrew.crewcast.media.ConversionStartedBy startedBy,
        String startedByLabel) {
}
