package com.solocrew.crewcast.desktop;

import com.solocrew.crewcast.facade.ConversionQueueSummary;
import com.solocrew.crewcast.facade.ShareListItem;
import com.solocrew.crewcast.media.CompatUiState;
import com.solocrew.crewcast.settings.ShareEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertNowShareListTest {

    private MainFrame frame;

    @BeforeEach
    void setUp() {
        frame = new MainFrame();
        frame.testSetSharingService(null);
        frame.testSetUiCompatStub(true, 12);
        frame.testSetConversionQueueSummary(new ConversionQueueSummary(1, 0, 0));
        frame.testSeedCachedShareList(List.of(neededItem()));
    }

    @Test
    void convertNowLeavesCheckingAndShowsConvertingImmediately() {
        frame.testSimulateConvertNow(neededItem());
        assertFalse(frame.testLastApplyUsedChecking, "Convert now must not show skeleton Checking…");
        assertEquals(CompatUiState.CONVERTING, frame.testFirstItemCompatState());
    }

    @Test
    void convertNowWhenQueuedShowsQueuedNotChecking() {
        frame.testSetConversionQueueSummary(new ConversionQueueSummary(0, 1, 0));
        frame.testSimulateConvertNow(neededItem());
        assertFalse(frame.testLastApplyUsedChecking);
        assertEquals(CompatUiState.QUEUED, frame.testFirstItemCompatState());
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
