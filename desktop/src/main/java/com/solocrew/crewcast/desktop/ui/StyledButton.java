package com.solocrew.crewcast.desktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.solocrew.crewcast.desktop.theme.CrewcastTheme;

import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusEvent;

public class StyledButton extends JButton {

    public enum Variant {
        PRIMARY, SECONDARY, DANGER, GHOST, ICON
    }

    private static final int H_PAD = 14;

    private Variant variant = Variant.SECONDARY;
    private boolean hovered;
    private boolean pressed;
    private boolean hideFocusRingOnMouse;
    private FocusEvent.Cause focusCause = FocusEvent.Cause.UNKNOWN;

    public StyledButton(String text) {
        super(text);
        init();
    }

    public StyledButton(FlatSVGIcon icon) {
        super(icon);
        init();
        setVariant(Variant.ICON);
    }

    public StyledButton(String text, FlatSVGIcon icon) {
        super(text, icon);
        init();
        setHorizontalTextPosition(RIGHT);
        setIconTextGap(6);
    }

    public void setHideFocusRingOnMouse(boolean hideFocusRingOnMouse) {
        this.hideFocusRingOnMouse = hideFocusRingOnMouse;
    }

    private void init() {
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setFont(Fonts.ui(600, 12));
        setForeground(CrewcastTheme.TEXT);
        setBorder(new EmptyBorder(0, H_PAD, 0, H_PAD));
        addChangeListener(e -> repaint());
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                focusCause = e.getCause();
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                focusCause = FocusEvent.Cause.UNKNOWN;
                repaint();
            }
        });
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (isEnabled()) {
                    hovered = true;
                    repaint();
                }
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                hovered = false;
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (isEnabled()) {
                    pressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                pressed = false;
                repaint();
            }
        });
        packToContent();
    }

    public void packToContent() {
        if (getFont() == null) {
            return;
        }
        if (variant == Variant.ICON) {
            Dimension d = new Dimension(30, 30);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            return;
        }
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D scratch = img.createGraphics();
        try {
            TextPaint.prepare(scratch);
            int textW = getText() == null || getText().isEmpty()
                    ? 0
                    : TextPaint.stringWidth(scratch, getFont(), getText());
            int iconW = 0;
            if (getIcon() != null) {
                iconW = getIcon().getIconWidth();
                if (textW > 0) {
                    iconW += getIconTextGap();
                }
            }
            int w = H_PAD * 2 + textW + iconW;
            int h = variant == Variant.DANGER ? 36 : 32;
            Dimension d = new Dimension(Math.max(w, variant == Variant.GHOST ? w : 32), h);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        } finally {
            scratch.dispose();
        }
    }

    public void setVariant(Variant variant) {
        this.variant = variant;
        if (variant == Variant.ICON) {
            setBorder(new EmptyBorder(0, 0, 0, 0));
        } else if (variant == Variant.PRIMARY) {
            setFont(Fonts.ui(700, 12));
            setForeground(CrewcastTheme.ON_ACCENT);
            setBorder(new EmptyBorder(0, H_PAD, 0, H_PAD));
        } else if (variant == Variant.DANGER) {
            setFont(Fonts.ui(700, 13));
            setForeground(CrewcastTheme.DANGER_TEXT);
            setBorder(new EmptyBorder(0, H_PAD, 0, H_PAD));
        } else if (variant == Variant.GHOST) {
            setFont(Fonts.ui(600, 12));
            setForeground(CrewcastTheme.LINK);
            setBorder(new EmptyBorder(0, 0, 0, 0));
        } else {
            setFont(Fonts.ui(600, 12));
            setBorder(new EmptyBorder(0, H_PAD, 0, H_PAD));
        }
        packToContent();
        repaint();
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        if (getFont() != null) {
            packToContent();
        }
    }

    @Override
    public void setIcon(javax.swing.Icon icon) {
        super.setIcon(icon);
        if (getFont() != null) {
            packToContent();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        TextPaint.prepare(g2);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (!isEnabled()) {
            g2.setColor(CrewcastTheme.SECONDARY_BTN);
            if (variant != Variant.GHOST && variant != Variant.ICON) {
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc(), arc());
            }
            g2.setColor(CrewcastTheme.META);
            g2.setFont(getFont());
            paintCenter(g2);
            g2.dispose();
            return;
        }
        Color bg = switch (variant) {
            case PRIMARY -> pressed ? CrewcastTheme.ACCENT.darker() : hovered ? CrewcastTheme.ACCENT.brighter() : CrewcastTheme.ACCENT;
            case SECONDARY -> pressed ? CrewcastTheme.SECONDARY_BTN.darker() : hovered ? CrewcastTheme.SECONDARY_BTN.brighter() : CrewcastTheme.SECONDARY_BTN;
            case DANGER -> pressed ? CrewcastTheme.DANGER_BG.darker() : CrewcastTheme.DANGER_BG;
            case ICON -> pressed ? CrewcastTheme.ICON_BTN.darker() : hovered ? CrewcastTheme.ICON_BTN.brighter() : CrewcastTheme.ICON_BTN;
            case GHOST -> null;
        };
        if (bg != null) {
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc(), arc());
        }
        if (variant == Variant.SECONDARY || variant == Variant.DANGER) {
            g2.setColor(variant == Variant.DANGER ? CrewcastTheme.DANGER_BORDER : CrewcastTheme.SECONDARY_BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc(), arc());
        }
        if (showFocusRing()) {
            g2.setColor(CrewcastTheme.ACCENT);
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc(), arc());
        }
        paintCenter(g2);
        g2.dispose();
    }

    private boolean showFocusRing() {
        if (!isFocusOwner() || variant == Variant.GHOST) {
            return false;
        }
        if (hideFocusRingOnMouse && focusCause == FocusEvent.Cause.MOUSE_EVENT) {
            return false;
        }
        return true;
    }

    private void paintCenter(Graphics2D g2) {
        TextPaint.prepare(g2);
        g2.setColor(isEnabled() ? getForeground() : CrewcastTheme.META);
        g2.setFont(getFont());
        var fm = g2.getFontMetrics(getFont());
        if (getIcon() != null) {
            javax.swing.Icon icon = getIcon();
            int contentW = (getText() != null && !getText().isEmpty())
                    ? icon.getIconWidth() + getIconTextGap() + fm.stringWidth(getText())
                    : icon.getIconWidth();
            int x0 = (getWidth() - contentW) / 2;
            int iy = (getHeight() - icon.getIconHeight()) / 2;
            if (getText() != null && !getText().isEmpty()) {
                icon.paintIcon(this, g2, x0, iy);
                g2.drawString(getText(), x0 + icon.getIconWidth() + getIconTextGap(),
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            } else {
                icon.paintIcon(this, g2, x0, iy);
            }
        } else if (getText() != null) {
            int tw = fm.stringWidth(getText());
            g2.drawString(getText(), (getWidth() - tw) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    private int arc() {
        return variant == Variant.ICON ? 7 : (variant == Variant.DANGER ? 9 : CrewcastTheme.RADIUS_BUTTON);
    }

    @Override
    public Insets getInsets() {
        return new Insets(0, 0, 0, 0);
    }
}
