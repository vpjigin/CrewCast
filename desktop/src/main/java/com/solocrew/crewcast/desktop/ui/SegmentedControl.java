package com.solocrew.crewcast.desktop.ui;

import com.solocrew.crewcast.desktop.theme.CrewcastTheme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class SegmentedControl extends JPanel {

    private final String[] options;
    private int selected;
    private boolean interactive = true;
    private final List<ActionListener> listeners = new ArrayList<>();

    public SegmentedControl(String... options) {
        this.options = options;
        setOpaque(false);
        setPreferredSize(new Dimension(120, 28));
        setFocusable(true);
        setRequestFocusEnabled(false);
    }

    public int getSelectedIndex() {
        return selected;
    }

    public void setSelectedIndex(int index) {
        selected = Math.max(0, Math.min(options.length - 1, index));
        repaint();
    }

    public void addActionListener(ActionListener l) {
        listeners.add(l);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        interactive = enabled;
        setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
    }

    private int segmentAt(int x) {
        int segW = Math.max(1, getWidth() / options.length);
        int idx = x / segW;
        return Math.max(0, Math.min(options.length - 1, idx));
    }

    private void selectSegment(int idx) {
        if (idx < 0 || idx >= options.length) {
            return;
        }
        boolean changed = idx != selected;
        selected = idx;
        repaint();
        if (changed) {
            fireAction();
        }
    }

    private void fireAction() {
        ActionEvent ev = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "segment");
        listeners.forEach(l -> l.actionPerformed(ev));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        TextPaint.prepare(g2);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(CrewcastTheme.SEGMENT_BG);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 7, 7);
        g2.setColor(CrewcastTheme.INPUT_BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 7, 7);
        int segW = getWidth() / options.length;
        for (int i = 0; i < options.length; i++) {
            int x = 2 + i * (segW - 2);
            if (i == selected) {
                g2.setColor(CrewcastTheme.SEGMENT_SELECTED);
                g2.fillRoundRect(x, 2, segW - 4, getHeight() - 4, 5, 5);
            }
            g2.setColor(i == selected ? Color.WHITE : CrewcastTheme.MUTED);
            g2.setFont(Fonts.ui(700, 11));
            var fm = g2.getFontMetrics();
            int tw = fm.stringWidth(options[i]);
            g2.drawString(options[i], x + (segW - 4 - tw) / 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
        }
        g2.dispose();
    }

    {
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (!interactive || !javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                selectSegment(segmentAt(e.getX()));
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (interactive) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
            }
        });
    }
}
