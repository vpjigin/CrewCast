package com.solocrew.crewcast.desktop;

import com.solocrew.crewcast.desktop.share.ShareListController;
import com.solocrew.crewcast.desktop.share.ShareListUiMode;
import com.solocrew.crewcast.facade.ConversionQueueSummary;
import com.solocrew.crewcast.facade.ShareListItem;
import com.solocrew.crewcast.media.CompatUiState;
import com.solocrew.crewcast.settings.ShareEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConvertNowShareListTest {

    private ShareListController controller;

    @BeforeEach
    void setUp() {
        controller = new ShareListController();
        controller.setQueueSummaryOverride(new ConversionQueueSummary(1, 0, 0));
        controller.apply(List.of(neededItem()), ShareListUiMode.NORMAL);
    }

    @Test
    void convertNowLeavesCheckingAndShowsConvertingImmediately() {
        controller.simulateConvertNow(neededItem());
        assertFalse(controller.lastApplyUsedChecking(), "Convert now must not show skeleton Checking…");
        assertEquals(CompatUiState.CONVERTING, controller.firstItemCompatState());
    }

    @Test
    void convertNowWhenQueuedShowsQueuedNotChecking() {
        controller.setQueueSummaryOverride(new ConversionQueueSummary(0, 1, 0));
        controller.simulateConvertNow(neededItem());
        assertFalse(controller.lastApplyUsedChecking());
        assertEquals(CompatUiState.QUEUED, controller.firstItemCompatState());
    }

    private static ShareListItem neededItem() {
        return new ShareListItem(
                0,
                new ShareEntry("/tmp/sample-video.mkv", ShareEntry.Kind.FILE),
                "1 file · 933 MB",
                "AC3",
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
}
