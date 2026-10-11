package com.solocrew.crewcast.desktop.ui;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.solocrew.crewcast.desktop.theme.CrewcastTheme;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;

public class QrCodeTile extends JPanel {

    public static final int TILE = 120;
    public static final int QUIET = 6;
    public static final int ARC = 8;
    private static final int RENDER = 240;
    private static final int DISPLAY = TILE - QUIET * 2;

    private Image qrImage;
    private boolean placeholder = true;

    public QrCodeTile() {
        setOpaque(false);
        setPreferredSize(new Dimension(TILE, TILE));
        setMinimumSize(new Dimension(TILE, TILE));
        setMaximumSize(new Dimension(TILE, TILE));
    }

    public void showPlaceholder() {
        qrImage = null;
        placeholder = true;
        repaint();
    }

    public void setQrUrl(String url) {
        if (url == null || url.isBlank()) {
            showPlaceholder();
            return;
        }
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    url,
                    BarcodeFormat.QR_CODE,
                    RENDER,
                    RENDER,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            BufferedImage hi = MatrixToImageWriter.toBufferedImage(matrix);
            BufferedImage scaled = new BufferedImage(DISPLAY, DISPLAY, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(hi, 0, 0, DISPLAY, DISPLAY, null);
            g2.dispose();
            qrImage = scaled;
            placeholder = false;
            repaint();
        } catch (Exception ex) {
            showPlaceholder();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (placeholder) {
            g2.setColor(CrewcastTheme.INPUT_BG);
            g2.fillRoundRect(0, 0, TILE - 1, TILE - 1, ARC, ARC);
            g2.setColor(CrewcastTheme.DASHED);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{5, 5}, 0));
            g2.drawRoundRect(1, 1, TILE - 3, TILE - 3, ARC, ARC);
        } else {
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, TILE - 1, TILE - 1, ARC, ARC);
            if (qrImage != null) {
                g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, TILE, TILE, ARC, ARC));
                g2.drawImage(qrImage, QUIET, QUIET, DISPLAY, DISPLAY, null);
            }
        }
        g2.dispose();
    }
}
