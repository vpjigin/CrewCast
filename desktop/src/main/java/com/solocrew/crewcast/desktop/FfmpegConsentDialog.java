package com.solocrew.crewcast.desktop;

import com.solocrew.crewcast.desktop.theme.CrewcastTheme;
import com.solocrew.crewcast.desktop.ui.Fonts;
import com.solocrew.crewcast.desktop.ui.StyledButton;
import com.solocrew.crewcast.media.FfmpegDownloadAssets;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.FfmpegDownloadConsent;
import com.solocrew.crewcast.settings.SettingsStore;
import net.miginfocom.swing.MigLayout;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

class FfmpegConsentDialog extends JDialog {

    enum Result {
        NONE,
        DOWNLOAD,
        DECLINED,
        MANUAL
    }

    private Result result = Result.NONE;

    FfmpegConsentDialog(JFrame owner, FfmpegDownloadAssets.Plan plan, SettingsStore settingsStore) {
        super(owner, "Enable audio support?", true);
        setLayout(new BorderLayout());
        getContentPane().setBackground(CrewcastTheme.WINDOW);

        String size = FfmpegDownloadAssets.formatEstimatedSize(plan.estimatedBytes());
        String host = plan.consentSourceHost();
        JPanel body = new JPanel(new MigLayout("ins 20 24, gap 8, fillx", "[grow]", "[][][]"));
        body.setOpaque(false);
        JLabel msg = new JLabel("<html><body style='width:380px;line-height:1.45'>"
                + "Many videos (especially MKV files) use audio formats that browsers can't play. "
                + "Without ffmpeg, some videos will play with no sound in the browser. "
                + "Crewcast can download ffmpeg (~55 MB, host shown below) and convert audio "
                + "automatically when someone watches."
                + "</body></html>");
        msg.setFont(Fonts.ui(500, 13));
        msg.setForeground(CrewcastTheme.TEXT);
        JLabel details = new JLabel("<html><body style='width:380px;line-height:1.45;color:#8b94a3'>"
                + "Host: <b style='color:#e8eaed'>" + host + "</b><br>"
                + "Approximate size: ~" + size + "<br><br>"
                + "Source: " + host + ". Crewcast verifies the file's checksum before use."
                + "</body></html>");
        details.setFont(Fonts.ui(500, 12.5f));
        body.add(msg, "growx, wrap");
        body.add(details, "growx, gaptop 8, wrap");

        JPanel footer = new JPanel(new MigLayout("ins 12 20, gap 8", "[]push[]push[]", "[]"));
        footer.setBackground(CrewcastTheme.TITLE_BAR);
        StyledButton download = new StyledButton("Download");
        download.setVariant(StyledButton.Variant.PRIMARY);
        download.addActionListener(e -> {
            result = Result.DOWNLOAD;
            dispose();
        });
        StyledButton choose = new StyledButton("Choose existing…");
        choose.setVariant(StyledButton.Variant.GHOST);
        choose.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select ffmpeg binary");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                AppSettings s = settingsStore.getSettings();
                s.setFfmpegBinaryPath(f.getAbsolutePath());
                s.setFfmpegDownloadConsent(FfmpegDownloadConsent.MANUAL);
                settingsStore.update(s);
                result = Result.MANUAL;
                dispose();
            }
        });
        StyledButton notNow = new StyledButton("Not now");
        notNow.setVariant(StyledButton.Variant.SECONDARY);
        notNow.addActionListener(e -> {
            result = Result.DECLINED;
            dispose();
        });
        footer.add(download);
        footer.add(choose);
        footer.add(notNow);
        add(body, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(download);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (result == Result.NONE) {
                    result = Result.DECLINED;
                }
            }
        });
        pack();
        setLocationRelativeTo(owner);
    }

    Result getResult() {
        return result;
    }
}
