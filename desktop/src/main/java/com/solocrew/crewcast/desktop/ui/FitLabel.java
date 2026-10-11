package com.solocrew.crewcast.desktop.ui;

import javax.swing.JLabel;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * JLabel with extra horizontal space so Manrope metrics are not clipped at the right edge (HiDPI / fractional widths).
 */
public class FitLabel extends JLabel {

    public static final int WIDTH_SAFETY = 2;

    public FitLabel() {
        super();
    }

    public FitLabel(String text) {
        super(text);
    }

    private boolean usesHtml() {
        String t = getText();
        return t != null && t.toLowerCase().startsWith("<html");
    }

    @Override
    public Dimension getPreferredSize() {
        Font f = getFont();
        String text = getText();
        if (f == null || text == null || text.isEmpty() || usesHtml()) {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width + WIDTH_SAFETY * 2, d.height);
        }
        int w = TextPaint.stringWidth(f, text) + WIDTH_SAFETY * 2;
        FontMetrics fm = getFontMetrics(f);
        return new Dimension(w, fm.getHeight());
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension d = getPreferredSize();
        return new Dimension(Math.min(d.width, 24), d.height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        TextPaint.prepare(g2);
        g2.translate(WIDTH_SAFETY, 0);
        g2.clipRect(0, 0, Math.max(0, getWidth() - WIDTH_SAFETY * 2), getHeight());
        super.paintComponent(g2);
        g2.dispose();
    }
}
