package com.solocrew.crewcast.desktop;

import com.solocrew.crewcast.net.DownloadFailureDetails;
import com.solocrew.crewcast.desktop.theme.CrewcastTheme;
import com.solocrew.crewcast.desktop.ui.Fonts;
import com.solocrew.crewcast.desktop.ui.StyledButton;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

final class DownloadErrorDialog {

    private DownloadErrorDialog() {}

    static void show(Component parent, String title, String reason, DownloadFailureDetails details) {
        DownloadFailureDetails d = details != null ? details : DownloadFailureDetails.of(reason, null);
        JDialog dialog = new JDialog(javax.swing.SwingUtilities.getWindowAncestor(parent), title);
        dialog.setModalityType(java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new MigLayout("ins 16, fill, gap 8", "[grow]", "[][][]"));
        root.setBackground(CrewcastTheme.WINDOW);
        JLabel headline = new JLabel("<html>" + escapeHtml(reason) + "</html>");
        headline.setFont(Fonts.ui(500, 13));
        headline.setForeground(CrewcastTheme.TEXT);
        root.add(headline, "growx, wrap");

        StyledButton copy = new StyledButton("Copy details");
        copy.setVariant(StyledButton.Variant.SECONDARY);
        copy.packToContent();
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(d.copyText()), null);
        });
        StyledButton ok = new StyledButton("OK");
        ok.setVariant(StyledButton.Variant.PRIMARY);
        ok.packToContent();
        ok.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new MigLayout("ins 0, gap 8", "[]push[]", "[]"));
        buttons.setOpaque(false);
        buttons.add(copy);
        buttons.add(ok);
        root.add(buttons, "alignx right, wrap");
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    static void showDiagnostics(Component parent, String report) {
        JDialog dialog = new JDialog(javax.swing.SwingUtilities.getWindowAncestor(parent), "Diagnostics");
        dialog.setModalityType(java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new MigLayout("ins 16, fill, gap 8", "[grow]", "[grow][]"));
        root.setBackground(CrewcastTheme.WINDOW);
        JTextArea area = new JTextArea(report, 18, 60);
        area.setEditable(false);
        area.setFont(Fonts.mono(12));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        area.setBackground(CrewcastTheme.INPUT_BG);
        area.setForeground(CrewcastTheme.TEXT);
        root.add(area, "grow, wrap");

        StyledButton copy = new StyledButton("Copy");
        copy.setVariant(StyledButton.Variant.SECONDARY);
        copy.packToContent();
        copy.addActionListener(e ->
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(report), null));
        StyledButton ok = new StyledButton("OK");
        ok.setVariant(StyledButton.Variant.PRIMARY);
        ok.packToContent();
        ok.addActionListener(e -> dialog.dispose());
        JPanel buttons = new JPanel(new MigLayout("ins 0, gap 8", "[]push[]", "[]"));
        buttons.setOpaque(false);
        buttons.add(copy);
        buttons.add(ok);
        root.add(buttons, "alignx right");
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }
}
