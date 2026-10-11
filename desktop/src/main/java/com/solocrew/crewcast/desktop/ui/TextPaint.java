package com.solocrew.crewcast.desktop.ui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;

public final class TextPaint {

    private TextPaint() {
    }

    public static void prepare(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    public static FontMetrics metrics(Graphics2D g2, Font font) {
        prepare(g2);
        g2.setFont(font);
        return g2.getFontMetrics(font);
    }

    public static int stringWidth(Graphics2D g2, Font font, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        prepare(g2);
        FontRenderContext frc = g2.getFontRenderContext();
        return (int) Math.ceil(new TextLayout(text, font, frc).getAdvance());
    }

    public static int stringWidth(Font font, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            return stringWidth(g2, font, text);
        } finally {
            g2.dispose();
        }
    }
}
