package com.solocrew.crewcast.desktop;

import com.solocrew.crewcast.facade.ShareListItem;
import com.solocrew.crewcast.media.CompatUiState;
import com.solocrew.crewcast.media.ConversionStartedBy;
import com.solocrew.crewcast.settings.ShareEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareRowLayoutTest {

    private MainFrame frame;

    @BeforeEach
    void setUp() {
        frame = new MainFrame();
        frame.testSetSharingService(null);
        frame.testSetUiCompatStub(true, 12);
    }

    @Test
    void shareRowsAt400pxWidthForEachCompatState() {
        assertDoesNotThrow(() -> {
            layoutAt400(item(CompatUiState.NEEDED));
            layoutAt400(item(CompatUiState.QUEUED));
            layoutAt400(item(CompatUiState.CONVERTING));
            layoutAt400(item(CompatUiState.READY));
            layoutAt400(item(CompatUiState.FAILED));
            layoutAt400(item(CompatUiState.UNAVAILABLE));
        });
    }

    private void layoutAt400(ShareListItem item) {
        JPanel row = frame.testBuildShareRowMain(item);
        JPanel host = new JPanel(new BorderLayout());
        host.add(row, BorderLayout.NORTH);
        host.setSize(new Dimension(400, 900));
        row.setSize(new Dimension(400, row.getPreferredSize().height));
        host.validate();
        assertTrue(row.getHeight() > 36, "row height for " + item.fileCompatState());
        assertTrue(row.getPreferredSize().height > 36);
    }

    private static ShareListItem item(CompatUiState state) {
        return new ShareListItem(
                0,
                new ShareEntry("/tmp/sample-video.mkv", ShareEntry.Kind.FILE),
                "1 file · 933 MB",
                "AC3",
                "",
                false,
                "",
                state,
                state == CompatUiState.CONVERTING ? 42 : 0,
                state == CompatUiState.UNAVAILABLE ? "No encoder" : "",
                state == CompatUiState.NEEDED,
                state == CompatUiState.CONVERTING,
                state == CompatUiState.QUEUED ? 2 : 0,
                state == CompatUiState.CONVERTING ? 2.1 : null,
                state == CompatUiState.CONVERTING ? 720 : null,
                state == CompatUiState.CONVERTING ? ConversionStartedBy.VIEWER : null,
                state == CompatUiState.CONVERTING ? "iPhone · Safari" : null,
                List.of());
    }
}
