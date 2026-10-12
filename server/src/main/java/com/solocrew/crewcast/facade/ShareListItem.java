package com.solocrew.crewcast.facade;

import com.solocrew.crewcast.settings.ShareEntry;

public record ShareListItem(
        int index,
        ShareEntry entry,
        String meta,
        String audioMeta,
        String subtitleMeta,
        boolean hevcWarning,
        String compatSummary,
        com.solocrew.crewcast.media.CompatUiState fileCompatState,
        int fileCompatProgress,
        String fileCompatTooltip,
        boolean fileCompatCanPrepare,
        boolean fileCompatCanCancel,
        java.util.List<ShareVideoCompatRow> folderVideos) {
}
