package com.solocrew.crewcast.desktop;

import com.solocrew.crewcast.desktop.share.ShareListController;
import com.solocrew.crewcast.desktop.share.ShareListUiMode;
import com.solocrew.crewcast.facade.ShareListItem;
import com.solocrew.crewcast.media.CompatUiState;
import com.solocrew.crewcast.settings.ShareEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Share list row model updates when compat refresh lists are applied (simulates {@code refreshCompatFieldsOnItems}). */
class CompatProgressShareListTest {

    private ShareListController controller;
    private ShareListItem base;

    @BeforeEach
    void setUp() {
        controller = new ShareListController();
        base = neededItem();
        controller.apply(List.of(base), ShareListUiMode.NORMAL);
    }

    @Test
    void rowModelQueuedRunningReady() {
        controller.apply(List.of(withState(base, CompatUiState.QUEUED, 0, null, null)), ShareListUiMode.NORMAL);
        assertEquals(CompatUiState.QUEUED, controller.firstItemCompatState());

        controller.apply(List.of(withState(base, CompatUiState.CONVERTING, 37, null, null)), ShareListUiMode.NORMAL);
        assertEquals(CompatUiState.CONVERTING, controller.firstItemCompatState());
        assertEquals(37, controller.firstItemCompatProgress());

        controller.apply(List.of(withState(base, CompatUiState.READY, 100, null, null)), ShareListUiMode.NORMAL);
        assertEquals(CompatUiState.READY, controller.firstItemCompatState());
    }

    @Test
    void progressListenerStepsUpdateRowModelWithEta() {
        controller.apply(List.of(withState(base, CompatUiState.CONVERTING, 10, 600, 1.2)), ShareListUiMode.NORMAL);
        assertEquals(10, controller.firstItemCompatProgress());
        assertEquals(600, controller.firstItemCompatEtaSeconds());
        assertNotNull(controller.firstItemCompatSpeed());

        controller.apply(List.of(withState(base, CompatUiState.CONVERTING, 50, 240, 2.0)), ShareListUiMode.NORMAL);
        assertEquals(50, controller.firstItemCompatProgress());
        assertEquals(240, controller.firstItemCompatEtaSeconds());
        assertEquals(2.0, controller.firstItemCompatSpeed());
    }

    private static ShareListItem neededItem() {
        return new ShareListItem(
                0,
                new ShareEntry("/tmp/sample-video.mkv", ShareEntry.Kind.FILE),
                "933 MB · HEVC 10-bit · AC3",
                "",
                "",
                false,
                "",
                CompatUiState.NEEDED,
                0,
                "",
                true,
                false,
                0,
                null,
                null,
                null,
                null,
                List.of());
    }

    private static ShareListItem withState(
            ShareListItem item,
            CompatUiState state,
            int progress,
            Integer etaSeconds,
            Double speed) {
        return new ShareListItem(
                item.index(),
                item.entry(),
                item.meta(),
                item.audioMeta(),
                item.subtitleMeta(),
                item.hevcWarning(),
                item.compatSummary(),
                state,
                progress,
                item.fileCompatTooltip(),
                state == CompatUiState.NEEDED,
                state == CompatUiState.CONVERTING || state == CompatUiState.QUEUED,
                item.fileCompatQueueAhead(),
                speed,
                etaSeconds,
                item.fileCompatStartedBy(),
                item.fileCompatStartedByLabel(),
                item.folderVideos());
    }
}
