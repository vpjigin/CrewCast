package com.solocrew.crewcast.desktop.ui;

import com.solocrew.crewcast.desktop.theme.CrewcastTheme;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.Map;

/** Uppercase section heading with fixed letter spacing (PLAYBACK). */
public class SectionLabel extends FitLabel {

    private static final float TRACKING_EM = 0.8f / 11.5f;

    public SectionLabel(String text) {
        super(text.toUpperCase());
        Font base = Fonts.ui(700, 11.5f);
        setFont(base.deriveFont(Map.of(TextAttribute.TRACKING, TRACKING_EM)));
        setForeground(CrewcastTheme.SECTION_LABEL);
    }
}
