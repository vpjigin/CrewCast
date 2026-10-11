package com.solocrew.crewcast.desktop;

import com.solocrew.crewcast.desktop.theme.CrewcastTheme;
import com.solocrew.crewcast.desktop.ui.Fonts;
import com.solocrew.crewcast.desktop.ui.StyledButton;
import com.solocrew.crewcast.desktop.ui.ToggleSwitch;
import com.solocrew.crewcast.facade.SharingService;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.DefaultCaptionsMode;
import com.solocrew.crewcast.settings.PrepareAudioMode;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;

class SettingsDialog extends JDialog {

    private static final String[] AUDIO_LANGS = {"auto", "en", "hi", "ml", "ta", "te", "kn"};

    private final AppSettings workingCopy;
    private final SharingService sharingService;
    private boolean saved;
    private final JLabel ffmpegHintLabel;
    private final JLabel cacheSizeLabel;

    SettingsDialog(JFrame owner, AppSettings current, SharingService sharingService) {
        super(owner, "Settings", true);
        this.sharingService = sharingService;
        workingCopy = cloneSettings(current);
        setSize(620, 720);
        setMinimumSize(new Dimension(620, 720));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(CrewcastTheme.WINDOW);
        setLayout(new BorderLayout());

        JPanel header = new JPanel(new MigLayout("ins 0 16, gap 16", "[grow]", "[44!]"));
        header.setBackground(CrewcastTheme.TITLE_BAR);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, CrewcastTheme.BORDER));
        JLabel title = new JLabel("Settings", JLabel.CENTER);
        title.setFont(Fonts.ui(600, 13));
        title.setForeground(CrewcastTheme.TEXT_TITLE);
        header.add(title, "growx");
        add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new MigLayout("ins 6 24 16, gap 0, fillx", "[grow,fill]", "[]"));
        body.setBackground(CrewcastTheme.WINDOW);

        addSection(body, "Sharing");
        ToggleSwitch requireKey = addToggleRow(body, "Require private key in links",
                "Adds ?t= to every link so guessed addresses show nothing", workingCopy.isRequirePrivateKey());
        ToggleSwitch allowWifi = addToggleRow(body, "Allow same‑Wi‑Fi access",
                "Lets devices on your network connect directly", workingCopy.isAllowSameWifiAccess());

        addSection(body, "Tunnel");
        JComboBox<AppSettings.TunnelProtocol> protocol = addChoiceRow(body, "Connection type",
                "Switch to HTTP/2 if streaming keeps dropping", AppSettings.TunnelProtocol.values(),
                workingCopy.getTunnelProtocol());
        ToggleSwitch startTunnel = addToggleRow(body, "Start tunnel when sharing starts",
                "Turn off for Wi‑Fi‑only sharing", workingCopy.isStartTunnelWhenSharingStarts());

        JLabel cloudflaredPathLabel = new JLabel(pathLabelText(workingCopy.getCloudflaredBinaryPath()));
        cloudflaredPathLabel.setFont(Fonts.ui(500, 12.5f));
        cloudflaredPathLabel.setForeground(CrewcastTheme.META);
        StyledButton chooseCloudflared = new StyledButton("Choose cloudflared…");
        chooseCloudflared.setVariant(StyledButton.Variant.GHOST);
        chooseCloudflared.packToContent();
        chooseCloudflared.addActionListener(e -> {
            var chooser = new javax.swing.JFileChooser();
            chooser.setDialogTitle("Select cloudflared binary");
            chooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                workingCopy.setCloudflaredBinaryPath(f.getAbsolutePath());
                cloudflaredPathLabel.setText(pathLabelText(workingCopy.getCloudflaredBinaryPath()));
            }
        });
        StyledButton clearCloudflared = new StyledButton("Use default");
        clearCloudflared.setVariant(StyledButton.Variant.GHOST);
        clearCloudflared.packToContent();
        clearCloudflared.addActionListener(e -> {
            workingCopy.setCloudflaredBinaryPath(null);
            cloudflaredPathLabel.setText(pathLabelText(null));
        });
        JPanel cloudRow = new JPanel(new MigLayout("ins 9 0, gap 8, fillx", "[grow,fill][]", "[]"));
        cloudRow.setOpaque(false);
        cloudRow.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, CrewcastTheme.DIVIDER));
        JLabel cloudTitle = new JLabel("cloudflared binary");
        cloudTitle.setFont(Fonts.ui(600, 13));
        cloudTitle.setForeground(CrewcastTheme.TEXT);
        JLabel cloudDesc = new JLabel("<html><body style='width:320px;line-height:1.4;color:#8b94a3'>"
                + "Optional override when download fails or you use Homebrew. Install hint: "
                + "<b>" + com.solocrew.crewcast.tunnel.CloudflaredManager.installHint() + "</b></body></html>");
        cloudDesc.setFont(Fonts.ui(500, 12.5f));
        JPanel cloudText = new JPanel(new MigLayout("ins 0, gap 2, fillx", "[grow]", "[][][]"));
        cloudText.setOpaque(false);
        cloudText.add(cloudTitle, "wrap");
        cloudText.add(cloudDesc, "growx, wrap");
        cloudText.add(cloudflaredPathLabel, "growx, wrap");
        JPanel cloudBtns = new JPanel(new MigLayout("ins 0, gap 6", "[]", "[][]"));
        cloudBtns.setOpaque(false);
        cloudBtns.add(chooseCloudflared, "wrap");
        cloudBtns.add(clearCloudflared);
        cloudRow.add(cloudText, "growx");
        cloudRow.add(cloudBtns, "aligny top");
        body.add(cloudRow, "growx, wrap");

        addSection(body, "Playback");
        ToggleSwitch fixAudio = addToggleRow(body, "Fix audio for browsers",
                "Converts AC3 / E-AC3 / DTS to AAC in a cached MP4; requires ffmpeg",
                workingCopy.isFixAudioForBrowsers());
        fixAudio.setEnabled(sharingService.isFfmpegAvailable());

        JComboBox<PrepareAudioMode> prepareMode = addChoiceRow(body, "Prepare audio in advance",
                "Off = a track is converted only when a viewer picks it. Uses disk space: each language is a full copy of the video. "
                        + "Default / All tracks start conversion when sharing starts (All uses more disk).",
                PrepareAudioMode.values(), workingCopy.getPrepareAudioInAdvance());
        prepareMode.setEnabled(sharingService.isFfmpegAvailable() && fixAudio.isSelected());
        fixAudio.addActionListener(e -> {
            boolean on = sharingService.isFfmpegAvailable() && fixAudio.isSelected();
            prepareMode.setEnabled(on);
        });

        ffmpegHintLabel = new JLabel();
        ffmpegHintLabel.setFont(Fonts.ui(500, 12.5f));
        ffmpegHintLabel.setForeground(CrewcastTheme.DANGER_TEXT);
        updateFfmpegHint();
        body.add(ffmpegHintLabel, "gapleft 4, gaptop 4, wrap");

        StyledButton recheckFfmpeg = new StyledButton("Re-check ffmpeg");
        recheckFfmpeg.setVariant(StyledButton.Variant.GHOST);
        recheckFfmpeg.packToContent();
        recheckFfmpeg.addActionListener(e -> {
            sharingService.recheckFfmpeg();
            fixAudio.setEnabled(sharingService.isFfmpegAvailable());
            prepareMode.setEnabled(sharingService.isFfmpegAvailable() && fixAudio.isSelected());
            updateFfmpegHint();
        });
        StyledButton downloadFfmpeg = new StyledButton("Download ffmpeg");
        downloadFfmpeg.setVariant(StyledButton.Variant.SECONDARY);
        downloadFfmpeg.packToContent();
        downloadFfmpeg.addActionListener(e -> sharingService.downloadFfmpegInBackground());
        JPanel ffmpegBtnRow = new JPanel(new MigLayout("ins 0, gap 8", "[][]", "[]"));
        ffmpegBtnRow.setOpaque(false);
        ffmpegBtnRow.add(recheckFfmpeg);
        ffmpegBtnRow.add(downloadFfmpeg);
        body.add(ffmpegBtnRow, "gapleft 4, gaptop 2, wrap");

        JComboBox<String> audioLang = addChoiceRow(body, "Preferred audio language",
                "Auto picks the first track; otherwise matches this language when available",
                AUDIO_LANGS, workingCopy.getPreferredAudioLanguage());

        JComboBox<DefaultCaptionsMode> defaultCaptions = addChoiceRow(body, "Default captions",
                "Initial caption track in the web player when a viewer has not chosen before",
                DefaultCaptionsMode.values(), workingCopy.getDefaultCaptions());

        JComboBox<Integer> cacheGb = addChoiceRow(body, "Audio cache limit",
                "Converted audio/video remuxes are stored up to this size (LRU eviction)",
                new Integer[]{5, 10, 20, 50, 100}, workingCopy.getAudioCacheMaxGb());

        cacheSizeLabel = new JLabel();
        cacheSizeLabel.setFont(Fonts.ui(500, 12.5f));
        cacheSizeLabel.setForeground(CrewcastTheme.META);
        updateCacheLabel();
        body.add(cacheSizeLabel, "gapleft 4, gaptop 2, wrap");

        StyledButton clearCache = new StyledButton("Clear audio cache");
        clearCache.setVariant(StyledButton.Variant.SECONDARY);
        clearCache.packToContent();
        clearCache.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(this,
                    "Delete all converted audio cache files?",
                    "Clear cache",
                    JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                try {
                    sharingService.clearAudioCache();
                    updateCacheLabel();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Clear cache failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        body.add(clearCache, "gapleft 4, gaptop 2, wrap");

        JComboBox<Integer> thumbCount = addChoiceRow(body, "Chapter thumbnails",
                "How many frames the jump grid shows",
                new Integer[]{20, 16, 12, 8}, workingCopy.getThumbnailCount());

        addSection(body, "This computer");
        ToggleSwitch keepAwake = addToggleRow(body, "Keep computer awake while sharing",
                "Stops sleep from cutting off viewers", workingCopy.isKeepComputerAwake());
        ToggleSwitch openLogin = addToggleRow(body, "Open Crewcast at login",
                "Starts the app, never starts sharing by itself", workingCopy.isOpenAtLogin());

        StyledButton openLogs = new StyledButton("Open log folder");
        openLogs.setVariant(StyledButton.Variant.SECONDARY);
        openLogs.packToContent();
        openLogs.addActionListener(e -> openLogFolder());
        StyledButton runDiagnostics = new StyledButton("Run diagnostics");
        runDiagnostics.setVariant(StyledButton.Variant.SECONDARY);
        runDiagnostics.packToContent();
        runDiagnostics.addActionListener(e -> {
            String report = sharingService.runDiagnosticsReport();
            DownloadErrorDialog.showDiagnostics(this, report);
        });
        JPanel supportRow = new JPanel(new MigLayout("ins 0, gap 8", "[][]", "[]"));
        supportRow.setOpaque(false);
        supportRow.add(openLogs);
        supportRow.add(runDiagnostics);
        body.add(supportRow, "gapleft 4, gaptop 8, wrap");

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new MigLayout("ins 12 20, gap 8", "[grow][]", "[]"));
        footer.setBackground(CrewcastTheme.TITLE_BAR);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CrewcastTheme.BORDER));
        StyledButton cancel = new StyledButton("Cancel");
        cancel.setVariant(StyledButton.Variant.SECONDARY);
        cancel.packToContent();
        cancel.addActionListener(e -> dispose());
        StyledButton done = new StyledButton("Done");
        done.setVariant(StyledButton.Variant.PRIMARY);
        done.packToContent();
        done.addActionListener(e -> {
            workingCopy.setRequirePrivateKey(requireKey.isSelected());
            workingCopy.setAllowSameWifiAccess(allowWifi.isSelected());
            workingCopy.setTunnelProtocol((AppSettings.TunnelProtocol) protocol.getSelectedItem());
            workingCopy.setStartTunnelWhenSharingStarts(startTunnel.isSelected());
            workingCopy.setFixAudioForBrowsers(fixAudio.isSelected() && sharingService.isFfmpegAvailable());
            if (sharingService.isFfmpegAvailable() && fixAudio.isSelected()) {
                workingCopy.setPrepareAudioInAdvance((PrepareAudioMode) prepareMode.getSelectedItem());
            } else {
                workingCopy.setPrepareAudioInAdvance(PrepareAudioMode.OFF);
            }
            workingCopy.setPreferredAudioLanguage((String) audioLang.getSelectedItem());
            workingCopy.setDefaultCaptions((DefaultCaptionsMode) defaultCaptions.getSelectedItem());
            workingCopy.setAudioCacheMaxGb((Integer) cacheGb.getSelectedItem());
            workingCopy.setThumbnailCount((Integer) thumbCount.getSelectedItem());
            workingCopy.setKeepComputerAwake(keepAwake.isSelected());
            workingCopy.setOpenAtLogin(openLogin.isSelected());
            saved = true;
            dispose();
        });
        footer.add(cancel, "alignx right");
        footer.add(done);
        add(footer, BorderLayout.SOUTH);
    }

    private void updateFfmpegHint() {
        if (sharingService.isFfmpegAvailable()) {
            ffmpegHintLabel.setText("ffmpeg found — audio fix is available.");
            ffmpegHintLabel.setForeground(CrewcastTheme.SUCCESS_SUBTITLE);
        } else {
            ffmpegHintLabel.setText("<html>ffmpeg not found. Install: <b>" + sharingService.ffmpegInstallHint() + "</b></html>");
            ffmpegHintLabel.setForeground(CrewcastTheme.DANGER_TEXT);
        }
    }

    private void updateCacheLabel() {
        try {
            cacheSizeLabel.setText(String.format("Audio cache: %.2f GB used", sharingService.audioCacheSizeGb()));
        } catch (Exception e) {
            cacheSizeLabel.setText("Audio cache: —");
        }
    }

    private void openLogFolder() {
        java.nio.file.Path dir = sharingService.logsDirectory();
        try {
            java.nio.file.Files.createDirectories(dir);
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir.toFile());
            } else {
                JOptionPane.showMessageDialog(this, dir.toAbsolutePath(), "Log folder", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Could not open log folder", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void addSection(JPanel body, String title) {
        JLabel h = new JLabel(title.toUpperCase());
        h.setFont(Fonts.ui(700, 11));
        h.setForeground(CrewcastTheme.MUTED);
        body.add(h, "gaptop 18, gapbottom 6, wrap");
    }

    private static ToggleSwitch addToggleRow(JPanel body, String label, String desc, boolean on) {
        JPanel row = new JPanel(new MigLayout("ins 9 0, gap 16, fillx", "[grow,fill][38!]", "[]"));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, CrewcastTheme.DIVIDER));
        JLabel l = new JLabel(label);
        l.setFont(Fonts.ui(600, 13));
        l.setForeground(CrewcastTheme.TEXT);
        JLabel d = new JLabel("<html><body style='width:320px;line-height:1.4;color:#8b94a3'>" + desc + "</body></html>");
        d.setFont(Fonts.ui(500, 12.5f));
        JPanel text = new JPanel(new MigLayout("ins 0, gap 2, fillx", "[grow]", "[][]"));
        text.setOpaque(false);
        text.add(l, "wrap");
        text.add(d, "growx");
        ToggleSwitch toggle = new ToggleSwitch();
        toggle.setSelected(on);
        row.add(text, "growx");
        row.add(toggle, "aligny center");
        body.add(row, "growx, wrap");
        return toggle;
    }

    private static <T> JComboBox<T> addChoiceRow(JPanel body, String label, String desc, T[] values, T selected) {
        JPanel row = new JPanel(new MigLayout("ins 9 0, gap 16, fillx", "[grow,fill][pref]", "[]"));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, CrewcastTheme.DIVIDER));
        JLabel l = new JLabel(label);
        l.setFont(Fonts.ui(600, 13));
        JLabel d = new JLabel("<html><body style='width:320px;line-height:1.4;color:#8b94a3'>" + desc + "</body></html>");
        d.setFont(Fonts.ui(500, 12.5f));
        JPanel text = new JPanel(new MigLayout("ins 0, gap 2", "[grow]", "[][]"));
        text.setOpaque(false);
        text.add(l, "wrap");
        text.add(d, "growx");
        JComboBox<T> combo = new JComboBox<>(values);
        combo.setSelectedItem(selected);
        combo.setFont(Fonts.ui(600, 12));
        combo.setBackground(CrewcastTheme.SECONDARY_BTN);
        combo.setForeground(CrewcastTheme.TEXT);
        row.add(text, "growx");
        row.add(combo, "aligny center");
        body.add(row, "growx, wrap");
        return combo;
    }

    boolean isSaved() {
        return saved;
    }

    AppSettings result() {
        return workingCopy;
    }

    private static AppSettings cloneSettings(AppSettings s) {
        AppSettings copy = new AppSettings();
        copy.setShares(s.getShares());
        copy.setRequirePrivateKey(s.isRequirePrivateKey());
        copy.setAllowSameWifiAccess(s.isAllowSameWifiAccess());
        copy.setTunnelProtocol(s.getTunnelProtocol());
        copy.setStartTunnelWhenSharingStarts(s.isStartTunnelWhenSharingStarts());
        copy.setKeepComputerAwake(s.isKeepComputerAwake());
        copy.setPreferredAudioLanguage(s.getPreferredAudioLanguage());
        copy.setDefaultCaptions(s.getDefaultCaptions());
        copy.setThumbnailCount(s.getThumbnailCount());
        copy.setOpenAtLogin(s.isOpenAtLogin());
        copy.setFixAudioForBrowsers(s.isFixAudioForBrowsers());
        copy.setPrepareAudioInAdvance(s.getPrepareAudioInAdvance());
        copy.setAudioCacheMaxGb(s.getAudioCacheMaxGb());
        copy.setChapterThumbnails(s.isChapterThumbnails());
        copy.setCloudflaredBinaryPath(s.getCloudflaredBinaryPath());
        copy.setFfmpegBinaryPath(s.getFfmpegBinaryPath());
        copy.setFfmpegDownloadConsent(s.getFfmpegDownloadConsent());
        return copy;
    }

    private static String pathLabelText(String path) {
        if (path == null || path.isBlank()) {
            return "Using PATH or automatic download to ~/.crewcast/bin/";
        }
        return path;
    }
}
