package com.solocrew.crewcast.desktop;

import com.solocrew.crewcast.facade.ShareListItem;
import com.solocrew.crewcast.media.CompatUiState;
import com.solocrew.crewcast.settings.ShareEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Desktop row model updates when compat refresh lists are applied (simulates {@code refreshCompatFieldsOnItems}). */
class CompatProgressShareListTest {

    private MainFrame frame;
    private ShareListItem base;

    @BeforeEach
    void setUp() {
        frame = new MainFrame();
        frame.testSetSharingService(null);
        base = neededItem();
        frame.testSeedCachedShareList(List.of(base));
    }

    @Test
    void rowModelQueuedRunningReady() {
        frame.testApplyCompatRefresh(List.of(withState(base, CompatUiState.QUEUED, 0, null, null)));
        assertEquals(CompatUiState.QUEUED, frame.testFirstItemCompatState());

        frame.testApplyCompatRefresh(List.of(withState(base, CompatUiState.CONVERTING, 37, null, null)));
        assertEquals(CompatUiState.CONVERTING, frame.testFirstItemCompatState());
        assertEquals(37, frame.testFirstItemCompatProgress());

        frame.testApplyCompatRefresh(List.of(withState(base, CompatUiState.READY, 100, null, null)));
        assertEquals(CompatUiState.READY, frame.testFirstItemCompatState());
    }

    @Test
    void progressListenerStepsUpdateRowModelWithEta() {
        frame.testApplyCompatRefresh(List.of(withState(base, CompatUiState.CONVERTING, 10, 600, 1.2)));
        assertEquals(10, frame.testFirstItemCompatProgress());
        assertEquals(600, frame.testFirstItemCompatEtaSeconds());
        assertNotNull(frame.testFirstItemCompatSpeed());

        frame.testApplyCompatRefresh(List.of(withState(base, CompatUiState.CONVERTING, 50, 240, 2.0)));
        assertEquals(50, frame.testFirstItemCompatProgress());
        assertEquals(240, frame.testFirstItemCompatEtaSeconds());
        assertEquals(2.0, frame.testFirstItemCompatSpeed());
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
