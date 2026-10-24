package com.solocrew.crewcast.desktop.ui;

/** Ensures user-influenced strings are shown as plain text, not Swing HTML. */
public final class PlainTextLabels {

    private PlainTextLabels() {
    }

    public static String forUi(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.indexOf('<') >= 0 || value.indexOf('&') >= 0) {
            return "Viewer";
        }
        return value;
    }
}
