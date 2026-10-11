package com.solocrew.crewcast.desktop.ui;

import com.solocrew.crewcast.desktop.theme.CrewcastTheme;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class ToggleSwitch extends JComponent {

    private boolean selected;
    private boolean hovered;
    private boolean pressed;
    private boolean focusRing;
    private final List<ActionListener> listeners = new ArrayList<>();

    public ToggleSwitch() {
        setOpaque(false);
        setPreferredSize(new Dimension(38, 22));
        setMinimumSize(new Dimension(38, 22));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) {
                    hovered = true;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    pressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isEnabled() && pressed && contains(e.getPoint())) {
                    setSelected(!selected);
                    fireAction();
                }
                pressed = false;
                repaint();
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                focusRing = true;
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                focusRing = false;
                repaint();
            }
        });
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    public void addActionListener(ActionListener l) {
        listeners.add(l);
    }

    private void fireAction() {
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "toggle");
        for (ActionListener l : listeners) {
            l.actionPerformed(e);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color track = !isEnabled() ? CrewcastTheme.TOGGLE_OFF.darker()
                : selected ? (pressed ? CrewcastTheme.ACCENT.darker() : CrewcastTheme.ACCENT)
                : CrewcastTheme.TOGGLE_OFF;
        if (hovered && isEnabled() && !selected) {
            track = CrewcastTheme.TOGGLE_OFF.brighter();
        }
        g2.setColor(track);
        g2.fillRoundRect(0, 0, 38, 22, CrewcastTheme.RADIUS_TOGGLE, CrewcastTheme.RADIUS_TOGGLE);
        int knobX = selected ? 38 - 2 - 18 : 2;
        g2.setColor(Color.WHITE);
        g2.fillOval(knobX, 2, 18, 18);
        if (focusRing && isFocusOwner()) {
            g2.setColor(CrewcastTheme.ACCENT);
            g2.drawRoundRect(0, 0, 37, 21, CrewcastTheme.RADIUS_TOGGLE, CrewcastTheme.RADIUS_TOGGLE);
        }
        g2.dispose();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        repaint();
    }
}
