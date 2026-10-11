package com.solocrew.crewcast.desktop.ui;

import com.solocrew.crewcast.desktop.theme.CrewcastTheme;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class StatusDot extends JComponent {

    private java.awt.Color color = CrewcastTheme.SUCCESS;
    private boolean glow = true;

    public StatusDot() {
        setOpaque(false);
        setPreferredSize(new Dimension(12, 12));
        setMinimumSize(new Dimension(12, 12));
    }

    public void setColor(java.awt.Color color) {
        this.color = color;
        repaint();
    }

    public void setGlow(boolean glow) {
        this.glow = glow;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        if (glow) {
            g2.setColor(new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue(), 46));
            g2.fillOval(cx - 6, cy - 6, 12, 12);
        }
        g2.setColor(color);
        g2.fillOval(cx - 5, cy - 5, 10, 10);
        g2.dispose();
    }
}
