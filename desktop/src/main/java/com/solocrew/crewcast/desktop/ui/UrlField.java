package com.solocrew.crewcast.desktop.ui;

import com.solocrew.crewcast.desktop.theme.CrewcastTheme;

import javax.swing.JTextField;
import javax.swing.text.DefaultCaret;
import java.awt.Dimension;
import java.awt.Font;

public class UrlField extends JTextField {

    private static final int ROW_HEIGHT = 40;

    private String fullText = "";

    public UrlField() {
        setEditable(false);
        setFont(Fonts.mono(12.5f));
        setForeground(CrewcastTheme.URL_TEXT);
        setBackground(CrewcastTheme.INPUT_BG);
        setMinimumSize(new Dimension(0, ROW_HEIGHT));
        setCaret(new DefaultCaret() {
            @Override
            public void paint(java.awt.Graphics g) {
            }
        });
        setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 12, 0, 8));
    }

    public void setUrl(String url) {
        fullText = url != null ? url : "";
        setToolTipText(fullText.isBlank() ? null : fullText);
        elide();
    }

    public void setPlaceholder(String placeholder) {
        fullText = "";
        setToolTipText(null);
        setText(placeholder != null ? placeholder : "");
        setForeground(CrewcastTheme.PLACEHOLDER);
    }

    public void setActiveUrl(String url) {
        setForeground(CrewcastTheme.URL_TEXT);
        setUrl(url);
    }

    /** Full URL for copy/open — never the ellipsized display text. */
    public String getFullUrl() {
        return fullText != null ? fullText : "";
    }

    @Override
    public Dimension getPreferredSize() {
        Font f = getFont();
        int h = f != null ? getFontMetrics(f).getHeight() + 12 : ROW_HEIGHT;
        return new Dimension(0, Math.max(ROW_HEIGHT, h));
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, ROW_HEIGHT);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        if (fullText != null && !fullText.isEmpty()) {
            elide();
        }
    }

    private void elide() {
        if (fullText == null || fullText.isEmpty()) {
            return;
        }
        Font f = getFont();
        if (f == null) {
            setText(fullText);
            return;
        }
        int w = getWidth() > 0 ? Math.max(0, getWidth() - 20 - FitLabel.WIDTH_SAFETY * 2) : Integer.MAX_VALUE;
        if (TextPaint.stringWidth(f, fullText) + FitLabel.WIDTH_SAFETY <= w) {
            setText(fullText);
            return;
        }
        String ell = "…";
        for (int i = fullText.length() - 1; i > 0; i--) {
            String s = fullText.substring(0, i) + ell;
            if (TextPaint.stringWidth(f, s) + FitLabel.WIDTH_SAFETY <= w) {
                setText(s);
                return;
            }
        }
        setText(ell);
    }
}
