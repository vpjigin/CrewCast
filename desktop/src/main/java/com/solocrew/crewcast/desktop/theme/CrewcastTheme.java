package com.solocrew.crewcast.desktop.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.solocrew.crewcast.desktop.ui.Fonts;

import java.awt.Color;
import java.awt.Font;
import java.util.Objects;

import javax.swing.UIManager;

public final class CrewcastTheme {

    public static final Color WINDOW = color(0x14171c);
    public static final Color TITLE_BAR = color(0x1a1e25);
    public static final Color BORDER = color(0x262b34);
    public static final Color CARD = color(0x1b1f26);
    public static final Color INPUT_BG = color(0x0f1216);
    public static final Color INPUT_BORDER = color(0x2a2f38);
    public static final Color ICON_TILE = color(0x252b36);
    public static final Color TEXT = color(0xe8ebf0);
    public static final Color TEXT_TITLE = color(0xc9d0db);
    /** Body / meta / subtitles (brighter muted). */
    public static final Color META = color(0xa9b2c0);
    /** URL placeholders when sharing is off. */
    public static final Color PLACEHOLDER = color(0x8b94a3);
    /** Status bar and PLAYBACK section label. */
    public static final Color SECTION_LABEL = color(0x9aa4b4);
    /** @deprecated use {@link #META} or {@link #PLACEHOLDER} */
    @Deprecated
    public static final Color MUTED = META;
    public static final Color LINK = color(0x9db4ff);
    public static final Color ACCENT = color(0x5b8cff);
    public static final Color ON_ACCENT = color(0x0b1020);
    public static final Color SUCCESS = color(0x34d399);
    public static final Color SUCCESS_SUBTITLE = color(0x9fb8ad);
    public static final Color SECONDARY_BTN = color(0x1f242c);
    public static final Color SECONDARY_BORDER = color(0x2f3541);
    public static final Color ICON_BTN = color(0x232933);
    public static final Color DANGER_BG = color(0x2a1a20);
    public static final Color DANGER_BORDER = color(0x6b3340);
    public static final Color DANGER_TEXT = color(0xff8da1);
    public static final Color TOGGLE_OFF = color(0x3a404b);
    public static final Color DASHED = color(0x323945);
    public static final Color BANNER_BG = color(0x1c2233);
    public static final Color BANNER_BORDER = color(0x2a3350);
    public static final Color BANNER_MUTED = color(0x9aa7c7);
    public static final Color STATUS_GRADIENT_START = color(0x17352b);
    public static final Color STATUS_BORDER = color(0x24493b);
    public static final Color VIEWER_ROW = color(0x14171c);
    public static final Color DIVIDER = color(0x20252d);
    public static final Color URL_TEXT = color(0xd5dbe6);
    public static final Color SEGMENT_BG = color(0x0f1216);
    public static final Color SEGMENT_SELECTED = color(0x2b3240);
    public static final Color STATUS_DOT_OFF = color(0x5c6574);

    public static final int RADIUS_CARD = 12;
    public static final int RADIUS_ROW = 10;
    public static final int RADIUS_INPUT = 9;
    public static final int RADIUS_BUTTON = 8;
    public static final int RADIUS_TOGGLE = 11;

    private static boolean fontsLoaded;

    private CrewcastTheme() {
    }

    public static void install() {
        try {
            Fonts.load();
            fontsLoaded = true;
            applyUiDefaults();
        } catch (Exception e) {
            fontsLoaded = false;
            System.err.println("Crewcast: bundled fonts unavailable: " + e.getMessage());
        }
        FlatLaf.registerCustomDefaultsSource("com.solocrew.crewcast.desktop.theme");
        FlatDarkLaf.setup();
        if (fontsLoaded) {
            applyUiDefaults();
        }
    }

    private static void applyUiDefaults() {
        Font label = Fonts.ui(500, 13);
        UIManager.put("defaultFont", label);
        UIManager.put("Label.font", label);
        UIManager.put("Button.font", Fonts.ui(600, 12));
        UIManager.put("TextField.font", Fonts.mono(12.5f));
        UIManager.put("Panel.font", label);
        UIManager.put("ToolTip.font", Fonts.ui(500, 12));
    }

    public static boolean usedBundledFonts() {
        return fontsLoaded;
    }

    private static Color color(int rgb) {
        return new Color(rgb);
    }
}
