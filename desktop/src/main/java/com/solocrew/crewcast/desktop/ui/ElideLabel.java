package com.solocrew.crewcast.desktop.ui;

import javax.swing.JLabel;
import java.awt.Font;
import java.awt.Graphics2D;

public class ElideLabel extends JLabel {

    private static final int WIDTH_SAFETY = FitLabel.WIDTH_SAFETY;

    private String fullText = "";

    public ElideLabel() {
        setVerticalAlignment(TOP);
    }

    public void setFullText(String text) {
        this.fullText = text != null ? text : "";
        setToolTipText(fullText.isBlank() ? null : fullText);
        elide();
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        if (isDisplayable() || getWidth() > 0) {
            elide();
        } else if (fullText != null && !fullText.isEmpty()) {
            setText(fullText);
        }
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        elide();
    }

    private void elide() {
        if (fullText == null || fullText.isEmpty()) {
            setText("");
            return;
        }
        Font f = getFont();
        if (f == null) {
            setText(fullText);
            return;
        }
        int w = getWidth() > 0 ? Math.max(0, getWidth() - WIDTH_SAFETY * 2) : Integer.MAX_VALUE;
        int fullW = TextPaint.stringWidth(f, fullText);
        if (fullW + WIDTH_SAFETY <= w) {
            setText(fullText);
            return;
        }
        String ell = "…";
        for (int i = fullText.length() - 1; i > 0; i--) {
            String s = fullText.substring(0, i) + ell;
            if (TextPaint.stringWidth(f, s) + WIDTH_SAFETY <= w) {
                setText(s);
                return;
            }
        }
        setText(ell);
    }

    /**
     * Width 0 so MigLayout uses the parent width for elision (full-text width would overflow scroll viewports).
     */
    @Override
    public java.awt.Dimension getPreferredSize() {
        Font f = getFont();
        int h = f != null ? getFontMetrics(f).getHeight() : super.getPreferredSize().height;
        return new java.awt.Dimension(0, h);
    }

    @Override
    public java.awt.Dimension getMinimumSize() {
        java.awt.Dimension d = getPreferredSize();
        return new java.awt.Dimension(0, d.height);
    }
}
