package com.solocrew.crewcast.desktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.solocrew.crewcast.desktop.theme.CrewcastTheme;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Muted × remove control for share rows. */
public class ShareRemoveButton extends StyledButton {

    private Color iconColor = CrewcastTheme.PLACEHOLDER;

    public ShareRemoveButton() {
        super(Icons.of("x", 15, CrewcastTheme.PLACEHOLDER));
        setVariant(Variant.ICON);
        setToolTipText("Remove from list");
        setPreferredSize(new Dimension(28, 28));
        setMinimumSize(new Dimension(28, 28));
        setMaximumSize(new Dimension(28, 28));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (getModel().isRollover() && isEnabled()) {
            g2.setColor(CrewcastTheme.DANGER_BG);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 7, 7);
            iconColor = CrewcastTheme.DANGER_TEXT;
        } else {
            iconColor = CrewcastTheme.PLACEHOLDER;
        }
        FlatSVGIcon icon = Icons.of("x", 15, iconColor);
        int ix = (getWidth() - icon.getIconWidth()) / 2;
        int iy = (getHeight() - icon.getIconHeight()) / 2;
        icon.paintIcon(this, g2, ix, iy);
        g2.dispose();
    }
}
