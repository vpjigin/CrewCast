package com.solocrew.crewcast.desktop.ui;

import javax.swing.JTextArea;
import javax.swing.text.View;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

/**
 * Non-editable, wrapping plain-text label. Width comes from the layout; height grows with wrapped lines.
 */
public class WrapLabel extends JTextArea {

    private static final int MAX_WRAP_PROBE_HEIGHT = 4096;

    private String fullText = "";

    public WrapLabel() {
        setEditable(false);
        setFocusable(false);
        setOpaque(false);
        setBorder(null);
        setLineWrap(true);
        setWrapStyleWord(true);
        setMargin(new Insets(0, 0, 0, 0));
    }

    public void setFullText(String text) {
        fullText = text != null ? text : "";
        setToolTipText(null);
        applyFullText();
        if (getDocument() != null) {
            revalidate();
        }
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        applyFullText();
    }

    private void applyFullText() {
        if (getDocument() == null) {
            return;
        }
        setText(fullText);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        if (getDocument() == null || width <= 0) {
            super.setBounds(x, y, width, height);
            return;
        }
        int preferredH = heightForWidth(width).height;
        super.setBounds(x, y, width, Math.max(height, preferredH));
    }

    /**
     * Width 0 so MigLayout uses the parent width for wrapping.
     */
    @Override
    public Dimension getPreferredSize() {
        if (getDocument() == null) {
            return new Dimension(0, 0);
        }
        int w = getWidth();
        if (w <= 0) {
            int line = getFontMetrics(getFont()).getHeight();
            Insets insets = getInsets();
            return new Dimension(0, line + insets.top + insets.bottom);
        }
        return heightForWidth(w);
    }

    private Dimension heightForWidth(int width) {
        try {
            Insets insets = getInsets();
            int contentW = Math.max(1, width - insets.left - insets.right);
            View root = getUI().getRootView(this);
            if (root != null) {
                root.setSize(contentW, MAX_WRAP_PROBE_HEIGHT);
                float span = root.getPreferredSpan(View.Y_AXIS);
                int h = (int) Math.ceil(span) + insets.top + insets.bottom;
                return new Dimension(0, Math.max(h, getFontMetrics(getFont()).getHeight()));
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        int line = getFontMetrics(getFont()).getHeight();
        Insets insets = getInsets();
        return new Dimension(0, line + insets.top + insets.bottom);
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension d = getPreferredSize();
        return new Dimension(0, d.height);
    }
}
