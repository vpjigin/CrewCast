package com.solocrew.crewcast.desktop.ui;

import com.solocrew.crewcast.desktop.theme.CrewcastTheme;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 8px progress bar — indeterminate (animated) or determinate (0–100%).
 */
public class LoadingBar extends JPanel {

    public enum Mode {
        INDETERMINATE,
        DETERMINATE
    }

    private Mode mode = Mode.INDETERMINATE;
    private int percent;
    private float indeterminatePos;
    private Timer animTimer;

    public LoadingBar() {
        setOpaque(false);
        setPreferredSize(new Dimension(0, 8));
        setMinimumSize(new Dimension(0, 8));
    }

    public void setIndeterminate(boolean indeterminate) {
        setMode(indeterminate ? Mode.INDETERMINATE : Mode.DETERMINATE);
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        if (mode == Mode.INDETERMINATE) {
            startAnimation();
        } else {
            stopAnimation();
        }
        repaint();
    }

    public void setPercent(int percent) {
        this.mode = Mode.DETERMINATE;
        stopAnimation();
        this.percent = Math.max(0, Math.min(100, percent));
        repaint();
    }

    private void startAnimation() {
        if (animTimer != null && animTimer.isRunning()) {
            return;
        }
        animTimer = new Timer(40, e -> {
            indeterminatePos += 0.04f;
            if (indeterminatePos > 1.5f) {
                indeterminatePos = 0f;
            }
            repaint();
        });
        animTimer.start();
    }

    private void stopAnimation() {
        if (animTimer != null) {
            animTimer.stop();
        }
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (!visible) {
            stopAnimation();
        } else if (mode == Mode.INDETERMINATE) {
            startAnimation();
        }
    }

    @Override
    public void removeNotify() {
        stopAnimation();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setColor(CrewcastTheme.INPUT_BORDER);
        g2.fillRoundRect(0, 0, w, h, 4, 4);
        if (w <= 0) {
            g2.dispose();
            return;
        }
        if (mode == Mode.DETERMINATE) {
            int fillW = Math.max(0, (int) Math.round(w * (percent / 100.0)));
            if (fillW > 0) {
                g2.setColor(CrewcastTheme.ACCENT);
                g2.fillRoundRect(0, 0, fillW, h, 4, 4);
            }
        } else {
            int segW = Math.max(24, w / 4);
            float travel = w + segW;
            int x = (int) (indeterminatePos * travel) - segW;
            g2.setColor(CrewcastTheme.ACCENT);
            g2.fillRoundRect(x, 0, segW, h, 4, 4);
        }
        g2.dispose();
    }
}
