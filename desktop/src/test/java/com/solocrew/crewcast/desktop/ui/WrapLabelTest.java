package com.solocrew.crewcast.desktop.ui;

import org.junit.jupiter.api.Test;

import java.awt.Font;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WrapLabelTest {

    @Test
    void setFontAndTextDuringConstructionLifecycle() {
        assertDoesNotThrow(() -> {
            WrapLabel label = new WrapLabel();
            label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            label.setFullText("hello");
            label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            label.setFullText("hello again");
        });
    }

    @Test
    void retainsFullTextAfterFontChange() {
        WrapLabel label = new WrapLabel();
        label.setFullText("line one");
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        assertEquals("line one", label.getText());
    }
}
