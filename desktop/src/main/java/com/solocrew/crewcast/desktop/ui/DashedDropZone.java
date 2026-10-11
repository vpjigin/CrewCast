package com.solocrew.crewcast.desktop.ui;

import com.solocrew.crewcast.desktop.theme.CrewcastTheme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class DashedDropZone extends JPanel {

    public DashedDropZone(String text) {
        setOpaque(false);
        setLayout(new java.awt.BorderLayout());
        JLabel label = new JLabel(text, JLabel.CENTER);
        label.setFont(Fonts.ui(500, 12.5f));
        label.setForeground(CrewcastTheme.META);
        add(label, java.awt.BorderLayout.CENTER);
        setPreferredSize(new java.awt.Dimension(10, 64));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(CrewcastTheme.DASHED);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6, 6}, 0));
        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, CrewcastTheme.RADIUS_ROW, CrewcastTheme.RADIUS_ROW);
        g2.dispose();
        super.paintComponent(g);
    }
}
