package com.solocrew.crewcast.desktop.ui;

import com.solocrew.crewcast.desktop.theme.CrewcastTheme;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

public class RoundedPanel extends JPanel {

    private int arc = CrewcastTheme.RADIUS_CARD;
    private Color fill = CrewcastTheme.CARD;
    private Color border = CrewcastTheme.BORDER;
    private Color gradientEnd;
    private Insets padding = new Insets(0, 0, 0, 0);
    private float panelAlpha = 1f;

    public RoundedPanel() {
        setOpaque(false);
    }

    public void setArc(int arc) {
        this.arc = arc;
        repaint();
    }

    public void setFill(Color fill) {
        this.fill = fill;
        repaint();
    }

    public void setBorderColor(Color border) {
        this.border = border;
        repaint();
    }

    public void setGradientEnd(Color gradientEnd) {
        this.gradientEnd = gradientEnd;
        repaint();
    }

    public void setAlpha(float panelAlpha) {
        this.panelAlpha = Math.max(0f, Math.min(1f, panelAlpha));
        repaint();
    }

    public void setPanelPadding(int top, int left, int bottom, int right) {
        padding = new Insets(top, left, bottom, right);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(top, left, bottom, right));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        if (panelAlpha < 1f) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, panelAlpha));
        }
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        if (gradientEnd != null) {
            g2.setPaint(new java.awt.GradientPaint(0, 0, fill, w, h, gradientEnd));
        } else {
            g2.setColor(fill);
        }
        g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
        g2.setColor(border);
        g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
        g2.dispose();
        super.paintComponent(g);
    }
}
