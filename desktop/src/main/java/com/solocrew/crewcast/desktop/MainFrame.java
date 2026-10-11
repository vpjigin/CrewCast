package com.solocrew.crewcast.desktop;

import com.solocrew.crewcast.desktop.theme.CrewcastTheme;
import com.solocrew.crewcast.desktop.ui.DashedDropZone;
import com.solocrew.crewcast.desktop.ui.ElideLabel;
import com.solocrew.crewcast.desktop.ui.FitLabel;
import com.solocrew.crewcast.desktop.ui.Fonts;
import com.solocrew.crewcast.desktop.ui.QrCodeTile;
import com.solocrew.crewcast.desktop.ui.ShareRemoveButton;
import com.solocrew.crewcast.desktop.ui.Icons;
import com.solocrew.crewcast.desktop.ui.SectionLabel;
import com.solocrew.crewcast.desktop.ui.LayoutDebug;
import com.solocrew.crewcast.desktop.ui.RoundedPanel;
import com.solocrew.crewcast.desktop.ui.SegmentedControl;
import com.solocrew.crewcast.desktop.ui.StatusDot;
import com.solocrew.crewcast.desktop.ui.StyledButton;
import com.solocrew.crewcast.desktop.ui.ToggleSwitch;
import com.solocrew.crewcast.desktop.ui.UrlField;
import com.solocrew.crewcast.desktop.ui.ViewportWidthPanel;
import com.solocrew.crewcast.facade.ShareListItem;
import com.solocrew.crewcast.facade.SharingLinks;
import com.solocrew.crewcast.facade.SharingService;
import com.solocrew.crewcast.facade.SharingUiListener;
import com.solocrew.crewcast.facade.TunnelState;
import com.solocrew.crewcast.facade.ViewerView;
import com.solocrew.crewcast.settings.AppSettings;
import com.solocrew.crewcast.settings.ShareEntry;
import net.miginfocom.swing.MigLayout;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

public class MainFrame extends JFrame implements SharingUiListener {

    private static final boolean MAC_OS =
            System.getProperty("os.name", "").toLowerCase().contains("mac");

    private ConfigurableApplicationContext context;
    private SharingService sharingService;

    private final JPanel rootPanel = new JPanel(new BorderLayout());
    private final JPanel mainArea = new JPanel(new BorderLayout());
    private final JPanel leftColumn = new JPanel();
    private final JPanel statusBar = new JPanel();
    private final ViewportWidthPanel rightScrollContent;
    private final JScrollPane rightScroll;

    private RoundedPanel statusCard;
    private RoundedPanel publicLinkCard;
    private RoundedPanel wifiLinkCard;
    private RoundedPanel qrCard;
    private StyledButton settingsButton;
    private StyledButton publicCopyBtn;
    private StyledButton publicOpenBtn;
    private StyledButton wifiCopyBtn;
    private StyledButton wifiOpenBtn;
    private StyledButton newLinkBtn;
    private QrCodeTile qrCodeTile;
    private final StyledButton removeAllSharesBtn = new StyledButton("Remove all");
    private JPanel tunnelErrRow;

    private final JPanel downloadBanner = new JPanel(new MigLayout("ins 12 20, gap 14, fill", "[pref!][grow,fill][220!]", "[]"));
    private final JLabel downloadTitle = new JLabel("Setting up the secure tunnel");
    private final JLabel downloadSubtitle = new JLabel();
    private final DownloadProgressBar downloadProgress = new DownloadProgressBar();
    private final JLabel downloadPercent = new JLabel("0%");

    private final JPanel listCards = new JPanel(new CardLayout());
    private final ViewportWidthPanel sharesContainer;
    private final JScrollPane shareScroll;
    private final JPanel emptyState = new JPanel();
    private final DashedDropZone compactDropZone = new DashedDropZone("Drop folders or video files here");

    private final ElideLabel shareHeaderLabel = new ElideLabel();
    private final JPanel shareHeaderButtons = new JPanel(new MigLayout("ins 0, gap 6", "[]", "[]"));

    private final ToggleSwitch chapterThumbnailsToggle = new ToggleSwitch();
    private final ToggleSwitch fixAudioToggle = new ToggleSwitch();
    private final JPanel playbackSection = new JPanel();

    private final JPanel rightLive = new JPanel();
    private final JPanel rightOnboarding = new JPanel();
    private final CardLayout rightCards = new CardLayout();
    private final JPanel rightCardHost = new JPanel(rightCards);

    private final StatusDot statusCardDot = new StatusDot();
    private final JLabel statusSharing = new JLabel("Sharing is off");
    private final JLabel statusViewers = new JLabel("0 people watching · tunnel idle");
    private final StyledButton sharingButton = new StyledButton("Start sharing");

    private final UrlField publicLinkField = new UrlField();
    private final UrlField wifiLinkField = new UrlField();
    private final JLabel tunnelErrorLabel = new JLabel(" ");
    private final StyledButton tunnelRetryButton = new StyledButton("Retry");

    private final SegmentedControl qrSegment = new SegmentedControl("Public", "Wi‑Fi");

    private final JPanel viewersPanel = new JPanel();

    private final StatusDot tunnelBarDot = new StatusDot();
    private final FitLabel tunnelBarText = new FitLabel();
    private final FitLabel serverPortLabel = new FitLabel();
    private final ElideLabel keepAwakeLabel = new ElideLabel();
    private final FitLabel statusBarVersion = new FitLabel();

    private final StyledButton onboardingStartButton = new StyledButton("Start sharing", Icons.of("play", 16, CrewcastTheme.MUTED));

    private boolean qrShowsPublic = true;
    private String lastTunnelError;
    private boolean lastTunnelRetryable;

    public MainFrame() {
        super("Crewcast");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        getContentPane().setBackground(CrewcastTheme.WINDOW);

        rightScrollContent = new ViewportWidthPanel(new MigLayout("ins 20 24 20 24, gap 14, fillx", "[grow,fill]", "[]"));
        rightScroll = new JScrollPane(rightScrollContent);
        rightScroll.setBorder(null);
        rightScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        rightScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        rightScroll.getViewport().setBackground(CrewcastTheme.WINDOW);
        rightScroll.getVerticalScrollBar().setUnitIncrement(16);

        sharesContainer = new ViewportWidthPanel(new MigLayout("ins 0, gap 8, fillx, wrap 1", "[grow,fill]", "[]"));
        shareScroll = new JScrollPane(sharesContainer);
        shareScroll.setBorder(null);
        shareScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        shareScroll.getViewport().setBackground(CrewcastTheme.WINDOW);
        shareScroll.getVerticalScrollBar().setUnitIncrement(16);

        buildUi();
        installDropTarget(sharesContainer);
        installDropTarget(emptyState);
        installDropTarget(compactDropZone);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                getRootPane().setDefaultButton(null);
                mainArea.setFocusable(true);
                mainArea.requestFocusInWindow();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                if (sharingService != null) {
                    sharingService.removeListener(MainFrame.this);
                    sharingService.shutdown();
                }
                if (context != null) {
                    context.close();
                }
                dispose();
                System.exit(0);
            }
        });

        Timer uiTimer = new Timer(1000, e -> refreshDynamicUi());
        uiTimer.start();

        getContentPane().setPreferredSize(new Dimension(1040, 680));
        getContentPane().setMinimumSize(new Dimension(960, 620));
        pack();
        revalidate();
        if (MAC_OS) {
            configureMacIntegratedTitleBar();
        }
        LayoutDebug.wire("contentPane", getContentPane());
        LayoutDebug.wire("mainArea", mainArea);
        LayoutDebug.wire("leftColumn", leftColumn);
        LayoutDebug.wire("rightColumn", rightScroll);
        LayoutDebug.wire("statusBar", statusBar);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                LayoutDebug.log("resize-contentPane", getContentPane());
                LayoutDebug.log("resize-mainArea", mainArea);
                LayoutDebug.log("resize-leftColumn", leftColumn);
                LayoutDebug.log("resize-rightColumn", rightScroll);
                LayoutDebug.log("resize-statusBar", statusBar);
            }
        });
    }

    private void configureMacIntegratedTitleBar() {
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
    }

    public void onSpringReady(ConfigurableApplicationContext ctx) {
        this.context = ctx;
        sharingService = ctx.getBean(SharingService.class);
        sharingService.addListener(this);

        chapterThumbnailsToggle.setSelected(sharingService.getSettings().isChapterThumbnails());
        syncFixAudioToggleFromSettings();

        reloadShareList();
        refreshDynamicUi();
    }

    @Override
    public void onServerReady(int port) {
        SwingUtilities.invokeLater(() -> serverPortLabel.setText(formatPortLabel(port)));
    }

    private void buildUi() {
        rootPanel.setBackground(CrewcastTheme.WINDOW);
        setContentPane(rootPanel);

        settingsButton = new StyledButton(Icons.of("sliders", 17, CrewcastTheme.META));
        settingsButton.setToolTipText("Settings");
        settingsButton.setVariant(StyledButton.Variant.ICON);
        settingsButton.setHideFocusRingOnMouse(true);
        settingsButton.addActionListener(e -> openSettings());
        sharingButton.setHideFocusRingOnMouse(true);
        onboardingStartButton.setHideFocusRingOnMouse(true);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        if (MAC_OS) {
            north.add(buildMacTitleBar(), BorderLayout.NORTH);
        }
        north.add(buildDownloadBanner(), BorderLayout.CENTER);
        rootPanel.add(north, BorderLayout.NORTH);

        buildLeftColumnContent();
        leftColumn.setPreferredSize(new Dimension(380, 10));
        leftColumn.setMinimumSize(new Dimension(380, 10));
        leftColumn.setMaximumSize(new Dimension(380, Integer.MAX_VALUE));

        buildRightColumnContent();
        mainArea.setBackground(CrewcastTheme.WINDOW);
        mainArea.add(leftColumn, BorderLayout.WEST);
        mainArea.add(rightScroll, BorderLayout.CENTER);
        rootPanel.add(mainArea, BorderLayout.CENTER);

        buildStatusBarContent();
        statusBar.setPreferredSize(new Dimension(10, 30));
        statusBar.setMinimumSize(new Dimension(10, 30));
        rootPanel.add(statusBar, BorderLayout.SOUTH);

        statusBarVersion.setText(formatVersionLabel(getClass().getPackage().getImplementationVersion()));
    }

    private JPanel buildMacTitleBar() {
        JPanel header = new JPanel(new MigLayout("ins 0 16, gap 16, fill", "[80!][grow,fill][80!]", "[44!]"));
        header.setBackground(CrewcastTheme.TITLE_BAR);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, CrewcastTheme.BORDER));
        header.setPreferredSize(new Dimension(10, 44));
        header.setMinimumSize(new Dimension(10, 44));

        JLabel title = new JLabel("Crewcast", JLabel.CENTER);
        title.setFont(Fonts.ui(700, 13));
        title.setForeground(CrewcastTheme.TEXT_TITLE);

        header.add(BoxPlaceholder.spacer(80), "cell 0 0");
        header.add(title, "cell 1 0, align center");
        header.add(settingsButton, "cell 2 0, align right");
        return header;
    }

    private JPanel buildDownloadBanner() {
        downloadBanner.setBackground(CrewcastTheme.BANNER_BG);
        downloadBanner.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, CrewcastTheme.BANNER_BORDER));
        downloadBanner.setVisible(false);

        JLabel icon = new JLabel(Icons.of("download", 18, CrewcastTheme.LINK));
        downloadTitle.setFont(Fonts.ui(700, 13));
        downloadTitle.setForeground(CrewcastTheme.TEXT);
        downloadSubtitle.setFont(Fonts.ui(500, 12.5f));
        downloadSubtitle.setForeground(CrewcastTheme.BANNER_MUTED);
        downloadSubtitle.setText("Downloading cloudflared once, so public links work. This only happens on first launch.");
        downloadPercent.setFont(Fonts.ui(500, 12));
        downloadPercent.setForeground(CrewcastTheme.BANNER_MUTED);
        downloadPercent.setHorizontalAlignment(JLabel.RIGHT);

        JPanel textCol = new JPanel(new MigLayout("ins 0, gap 0, fillx", "[grow,fill]", "[]0[]"));
        textCol.setOpaque(false);
        textCol.add(downloadTitle, "growx, wrap");
        textCol.add(downloadSubtitle, "growx");

        JPanel progressCol = new JPanel(new MigLayout("ins 0, gap 0, fillx", "[grow,fill]", "[]4[]"));
        progressCol.setOpaque(false);
        progressCol.add(downloadProgress, "growx, wrap");
        progressCol.add(downloadPercent, "growx, align right");

        downloadBanner.add(icon, "cell 0 0, aligny center");
        downloadBanner.add(textCol, "cell 1 0, growx");
        downloadBanner.add(progressCol, "cell 2 0, aligny center");
        return downloadBanner;
    }

    private void buildLeftColumnContent() {
        leftColumn.setLayout(new MigLayout("fill, ins 20, gap 14", "[grow,fill]", "[][][grow,fill][]"));
        leftColumn.setBackground(CrewcastTheme.WINDOW);
        leftColumn.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, CrewcastTheme.BORDER));

        shareHeaderLabel.setFullText("Nothing shared yet");
        shareHeaderLabel.setFont(Fonts.ui(500, 12.5f));
        shareHeaderLabel.setForeground(CrewcastTheme.META);

        StyledButton folderBtn = new StyledButton("Folder", Icons.of("plus", 14, CrewcastTheme.TEXT));
        folderBtn.setVariant(StyledButton.Variant.SECONDARY);
        folderBtn.addActionListener(e -> chooseFolder());
        StyledButton filesBtn = new StyledButton("Files", Icons.of("plus", 14, CrewcastTheme.TEXT));
        filesBtn.setVariant(StyledButton.Variant.SECONDARY);
        filesBtn.addActionListener(e -> chooseFiles());
        shareHeaderButtons.setLayout(new MigLayout("ins 0, gap 6", "[]", "[]"));
        shareHeaderButtons.setOpaque(false);
        shareHeaderButtons.add(folderBtn, "shrink 0, growx 0");
        shareHeaderButtons.add(filesBtn, "shrink 0, growx 0");

        removeAllSharesBtn.setVariant(StyledButton.Variant.GHOST);
        removeAllSharesBtn.setFont(Fonts.ui(600, 11));
        removeAllSharesBtn.setForeground(CrewcastTheme.META);
        removeAllSharesBtn.setVisible(false);
        removeAllSharesBtn.packToContent();
        removeAllSharesBtn.addActionListener(e -> confirmRemoveAllShares());

        ElideLabel shareTitleElide = new ElideLabel();
        shareTitleElide.setFullText("Shared videos");
        shareTitleElide.setFont(Fonts.ui(700, 15));
        shareTitleElide.setForeground(CrewcastTheme.TEXT);

        JPanel headerRow = new JPanel(new MigLayout("ins 0, gap 8, fillx", "[grow,fill][pref!, shrink 0]", "[][]"));
        headerRow.setOpaque(false);
        headerRow.add(shareTitleElide, "cell 0 0, growx, wmin 0");
        headerRow.add(shareHeaderButtons, "cell 1 0, aligny top");
        headerRow.add(shareHeaderLabel, "cell 0 1, growx, wmin 0");
        headerRow.add(removeAllSharesBtn, "cell 1 1, alignx right, aligny center");
        leftColumn.add(headerRow, "growx, wrap");

        buildEmptyState();
        JPanel listPanel = new JPanel(new MigLayout("fill, ins 0, gap 8", "[grow,fill]", "[grow,fill][]"));
        listPanel.setOpaque(false);
        listPanel.add(shareScroll, "grow, push, wrap");
        listPanel.add(compactDropZone, "growx");
        listCards.setOpaque(false);
        listCards.add(emptyState, "empty");
        listCards.add(listPanel, "list");
        leftColumn.add(listCards, "grow, push, wrap");

        buildPlaybackSection();
        leftColumn.add(playbackSection, "growx");
    }

    private void buildEmptyState() {
        emptyState.setLayout(new MigLayout("fill, ins 24, gap 12, align center", "[grow,fill]", "[][][][]"));
        emptyState.setOpaque(false);
        emptyState.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        RoundedPanel iconTile = new RoundedPanel();
        iconTile.setLayout(new MigLayout("ins 0, align center", "[]", "[]"));
        iconTile.setFill(CrewcastTheme.SECONDARY_BTN);
        iconTile.setBorderColor(CrewcastTheme.SECONDARY_BORDER);
        iconTile.setArc(14);
        iconTile.setPreferredSize(new Dimension(52, 52));
        iconTile.add(new JLabel(Icons.of("folder", 26, CrewcastTheme.LINK)), "align center");

        JLabel emptyTitle = new JLabel("Drop a folder or videos here", JLabel.CENTER);
        emptyTitle.setFont(Fonts.ui(700, 14));
        emptyTitle.setForeground(CrewcastTheme.TEXT);
        JLabel emptyHint = new JLabel(
                "<html><center style='max-width:230px'>mp4, mkv, mov, webm and m4v files inside will show up for viewers.</center>",
                JLabel.CENTER);
        emptyHint.setFont(Fonts.ui(500, 12.5f));
        emptyHint.setForeground(CrewcastTheme.MUTED);

        StyledButton addFolder = new StyledButton("Add folder", Icons.of("plus", 14, CrewcastTheme.ON_ACCENT));
        addFolder.setVariant(StyledButton.Variant.PRIMARY);
        addFolder.addActionListener(e -> chooseFolder());
        StyledButton addFiles = new StyledButton("Add files");
        addFiles.setVariant(StyledButton.Variant.SECONDARY);
        addFiles.addActionListener(e -> chooseFiles());

        JPanel actions = new JPanel(new MigLayout("ins 0, gap 8", "[][]", "[]"));
        actions.setOpaque(false);
        addFolder.packToContent();
        addFiles.packToContent();
        actions.add(addFolder, "shrink 0");
        actions.add(addFiles, "shrink 0");

        EmptyDropSurface surface = new EmptyDropSurface();
        surface.setLayout(new MigLayout("fill, ins 24, gap 12, align center", "[grow,fill]", "[][][][]"));
        surface.setOpaque(false);
        surface.add(iconTile, "align center, wrap");
        surface.add(emptyTitle, "align center, wrap");
        surface.add(emptyHint, "align center, wrap");
        surface.add(actions, "align center");

        emptyState.add(surface, "grow, push");
        installDropTarget(surface);
    }

    private void buildPlaybackSection() {
        playbackSection.setLayout(new MigLayout("ins 14 0 0 0, gap 12, fillx", "[grow,fill]", "[]12[]12[]"));
        playbackSection.setOpaque(false);
        playbackSection.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CrewcastTheme.BORDER));

        playbackSection.add(new SectionLabel("Playback"), "growx, wrap");

        fixAudioToggle.addActionListener(e -> {
            if (sharingService != null && sharingService.isFfmpegAvailable()) {
                AppSettings settings = sharingService.getSettings();
                settings.setFixAudioForBrowsers(fixAudioToggle.isSelected());
                sharingService.saveSettings(settings);
            }
        });
        playbackSection.add(playbackRow(
                "Fix audio for browsers",
                "Converts AC3 / E-AC3 / DTS to AAC (cached MP4); requires ffmpeg",
                fixAudioToggle), "growx, wrap");
        chapterThumbnailsToggle.addActionListener(e -> {
            if (sharingService != null) {
                AppSettings settings = sharingService.getSettings();
                settings.setChapterThumbnails(chapterThumbnailsToggle.isSelected());
                sharingService.saveSettings(settings);
            }
        });
        playbackSection.add(playbackRow(
                "Chapter thumbnails",
                "20-frame jump grid in the player",
                chapterThumbnailsToggle), "growx");
    }

    private JPanel playbackRow(String title, String subtitle, ToggleSwitch toggle) {
        JPanel row = new JPanel(new MigLayout("ins 0, gap 12, fillx", "[grow,fill][38!]", "[]"));
        row.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setFont(Fonts.ui(600, 13));
        t.setForeground(CrewcastTheme.TEXT);
        JLabel s = new JLabel("<html>" + subtitle + "</html>");
        s.setFont(Fonts.ui(500, 12.5f));
        s.setForeground(CrewcastTheme.MUTED);
        JPanel text = new JPanel(new MigLayout("ins 0, gap 0", "[grow,fill]", "[]2[]"));
        text.setOpaque(false);
        text.add(t, "growx, wrap");
        text.add(s, "growx");
        row.add(text, "growx, wmin 0");
        row.add(toggle, "aligny center");
        return row;
    }

    private void buildRightColumnContent() {
        buildRightLive();
        buildRightOnboarding();

        rightCardHost.setOpaque(false);
        rightCardHost.add(rightOnboarding, "onboarding");
        rightCardHost.add(rightLive, "live");

        rightScrollContent.removeAll();
        rightScrollContent.setLayout(new MigLayout("ins 20 24 20 24, gap 14, fillx", "[grow,fill]", "[]"));
        rightScrollContent.add(rightCardHost, "growx, wmin 0");
    }

    private void buildRightLive() {
        rightLive.setLayout(new MigLayout("ins 0, gap 14, fillx", "[grow,fill]", "[][][][][grow,fill]"));
        rightLive.setOpaque(false);

        rightLive.add(buildStatusCard(), "growx, wmin 0, wrap");
        publicLinkCard = buildPublicLinkCard();
        rightLive.add(publicLinkCard, "growx, wmin 0, wrap");
        wifiLinkCard = buildWifiLinkCard();
        rightLive.add(wifiLinkCard, "growx, wmin 0, wrap");
        rightLive.add(buildBottomRow(), "grow, push");
    }

    private RoundedPanel buildStatusCard() {
        RoundedPanel card = new RoundedPanel();
        statusCard = card;
        card.setLayout(new MigLayout("ins 16 18, gap 14, fillx", "[12!][grow,fill][pref!]", "[]"));
        card.setFill(CrewcastTheme.STATUS_GRADIENT_START);
        card.setGradientEnd(CrewcastTheme.CARD);
        card.setBorderColor(CrewcastTheme.STATUS_BORDER);

        statusSharing.setFont(Fonts.ui(800, 17));
        statusSharing.setForeground(CrewcastTheme.TEXT);
        statusViewers.setFont(Fonts.ui(500, 12.5f));
        statusViewers.setForeground(CrewcastTheme.SUCCESS_SUBTITLE);

        sharingButton.addActionListener(e -> toggleSharing());

        JPanel text = new JPanel(new MigLayout("ins 0, gap 0", "[grow,fill]", "[]3[]"));
        text.setOpaque(false);
        text.add(statusSharing, "growx, wrap");
        text.add(statusViewers, "growx");

        card.add(statusCardDot, "aligny center");
        card.add(text, "growx, wmin 0");
        sharingButton.packToContent();
        card.add(sharingButton, "aligny center, shrink 0");
        return card;
    }

    private RoundedPanel buildPublicLinkCard() {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new MigLayout("ins 14 16, gap 10, fillx, hidemode 3", "[grow,fill]", "[][][][]"));

        JPanel heading = linkCardHeading(Icons.of("globe", 16, CrewcastTheme.LINK),
                "Public link", "Anyone with this link, from anywhere");
        card.add(heading, "growx, wrap");
        card.add(urlRow(publicLinkField, true), "growx, wrap");

        newLinkBtn = new StyledButton("New link", Icons.of("refresh", 12, CrewcastTheme.LINK));
        newLinkBtn.setVariant(StyledButton.Variant.GHOST);
        newLinkBtn.addActionListener(e -> regenerateToken());

        JPanel sessionRow = new JPanel(new MigLayout("ins 0, gap 8, fillx", "[pref!][grow,fill][pref!]", "[]"));
        sessionRow.setOpaque(false);
        sessionRow.add(new JLabel(Icons.of("lock", 13, CrewcastTheme.MUTED)), "aligny center");
        JLabel sessionHint = new JLabel("The ?t= key is private to this session");
        sessionHint.setFont(Fonts.ui(500, 12.5f));
        sessionHint.setForeground(CrewcastTheme.MUTED);
        sessionRow.add(sessionHint, "growx, wmin 0");
        newLinkBtn.packToContent();
        sessionRow.add(newLinkBtn, "aligny center, shrink 0");
        card.add(sessionRow, "growx, wrap");

        tunnelErrorLabel.setFont(Fonts.ui(500, 12.5f));
        tunnelErrorLabel.setForeground(CrewcastTheme.DANGER_TEXT);
        tunnelRetryButton.setVariant(StyledButton.Variant.GHOST);
        tunnelRetryButton.setVisible(false);
        tunnelRetryButton.packToContent();
        tunnelRetryButton.addActionListener(e -> {
            if (sharingService != null) {
                sharingService.retryTunnel();
            }
        });
        tunnelErrRow = new JPanel(new MigLayout("ins 0, gap 8, fillx", "[grow,fill][pref!]", "[]"));
        tunnelErrRow.setOpaque(false);
        tunnelErrRow.setVisible(false);
        tunnelErrRow.add(tunnelErrorLabel, "growx, wmin 0");
        tunnelErrRow.add(tunnelRetryButton, "aligny center, shrink 0");
        card.add(tunnelErrRow, "growx");
        return card;
    }

    private RoundedPanel buildWifiLinkCard() {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new MigLayout("ins 14 16, gap 10, fillx", "[grow,fill]", "[][]"));

        JPanel heading = linkCardHeading(Icons.of("wifi", 16, CrewcastTheme.LINK),
                "Same Wi‑Fi link", "Fastest, no internet needed");
        card.add(heading, "growx, wrap");
        card.add(urlRow(wifiLinkField, false), "growx");
        return card;
    }

    private JPanel linkCardHeading(com.formdev.flatlaf.extras.FlatSVGIcon icon, String title, String subtitle) {
        JPanel row = new JPanel(new MigLayout("ins 0, gap 8, fillx", "[pref!][grow,fill]", "[]"));
        row.setOpaque(false);
        row.add(new JLabel(icon), "aligny top");
        FitLabel t = new FitLabel(title);
        t.setFont(Fonts.ui(600, 13));
        t.setForeground(CrewcastTheme.TEXT);
        FitLabel s = new FitLabel(subtitle);
        s.setFont(Fonts.ui(500, 12.5f));
        s.setForeground(CrewcastTheme.MUTED);
        JPanel text = new JPanel(new MigLayout("ins 0, gap 0", "[grow,fill]", "[]2[]"));
        text.setOpaque(false);
        text.add(t, "growx, wmin 0, shrink 0, wrap");
        text.add(s, "growx, wmin 0");
        row.add(text, "growx, wmin 0");
        return row;
    }

    private JPanel urlRow(UrlField field, boolean publicLink) {
        RoundedPanel fieldWrap = new RoundedPanel();
        fieldWrap.setLayout(new MigLayout("ins 0 4 0 0, gap 6, fillx", "[grow,fill][pref!][30!, shrink 0]", "[40!]"));
        fieldWrap.setFill(CrewcastTheme.INPUT_BG);
        fieldWrap.setBorderColor(CrewcastTheme.INPUT_BORDER);
        fieldWrap.setArc(CrewcastTheme.RADIUS_INPUT);

        StyledButton copy = new StyledButton("Copy", Icons.of("copy", 13, publicLink ? CrewcastTheme.ON_ACCENT : CrewcastTheme.TEXT));
        copy.setVariant(publicLink ? StyledButton.Variant.PRIMARY : StyledButton.Variant.SECONDARY);
        copy.addActionListener(e -> copyText(field.getFullUrl()));
        if (publicLink) {
            publicCopyBtn = copy;
        } else {
            wifiCopyBtn = copy;
        }

        StyledButton open = new StyledButton(Icons.of("external-link", 14, CrewcastTheme.TEXT_TITLE));
        open.setVariant(StyledButton.Variant.ICON);
        open.setToolTipText(publicLink ? "Open public link in browser" : "Open Wi-Fi link in browser");
        open.addActionListener(e -> openUrl(field.getFullUrl()));
        if (publicLink) {
            publicOpenBtn = open;
        } else {
            wifiOpenBtn = open;
        }

        copy.packToContent();
        fieldWrap.add(field, "growx, wmin 0");
        fieldWrap.add(copy, "aligny center, shrink 0");
        fieldWrap.add(open, "aligny center, shrink 0");

        JPanel row = new JPanel(new MigLayout("ins 0, fillx", "[grow,fill]", "[]"));
        row.setOpaque(false);
        row.add(fieldWrap, "growx, wmin 0");
        return row;
    }

    private JPanel buildBottomRow() {
        JPanel row = new JPanel(new MigLayout("fill, ins 0, gap 14", "[250!][grow,fill]", "[grow,fill]"));
        row.setOpaque(false);
        qrCard = buildQrCard();
        row.add(qrCard, "width 250!, growy, shrink 0");
        row.add(buildWatchingCard(), "grow, push, wmin 0");
        return row;
    }

    private RoundedPanel buildQrCard() {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new MigLayout("ins 14, gap 14, fill", "[120!][grow,fill]", "[160!]"));
        card.setPreferredSize(new Dimension(250, 160));
        card.setMinimumSize(new Dimension(250, 160));
        card.setMaximumSize(new Dimension(250, Integer.MAX_VALUE));

        qrCodeTile = new QrCodeTile();
        qrCodeTile.showPlaceholder();

        FitLabel scanTitle = new FitLabel(
                "<html><body style='line-height:1.3'>Scan with<br>your phone</body></html>");
        scanTitle.setFont(Fonts.ui(600, 13));
        scanTitle.setForeground(CrewcastTheme.TEXT);
        scanTitle.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        qrSegment.setPreferredSize(new Dimension(120, 28));
        qrSegment.addActionListener(e -> {
            qrShowsPublic = qrSegment.getSelectedIndex() == 0;
            updateQrImage();
        });

        JPanel right = new JPanel(new MigLayout("ins 0, gap 8, fill", "[grow,fill]", "[]10[]"));
        right.setOpaque(false);
        right.add(scanTitle, "growx, wmin 0, wrap");
        right.add(qrSegment, "growx");

        card.add(qrCodeTile, "aligny center, shrink 0");
        card.add(right, "aligny center, growx, wmin 0");
        return card;
    }

    private RoundedPanel buildWatchingCard() {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new MigLayout("ins 14, gap 8, fill", "[grow,fill]", "[][]"));

        JPanel heading = new JPanel(new MigLayout("ins 0, gap 8", "[]", "[]"));
        heading.setOpaque(false);
        heading.add(new JLabel(Icons.of("users", 16, CrewcastTheme.LINK)));
        FitLabel t = new FitLabel("Watching now");
        t.setFont(Fonts.ui(600, 13));
        t.setForeground(CrewcastTheme.TEXT);
        heading.add(t, "shrink 0");

        viewersPanel.setLayout(new MigLayout("ins 0, gap 8, fillx, wrap 1", "[grow,fill]", "[]"));
        viewersPanel.setOpaque(false);

        card.add(heading, "growx, wrap");
        card.add(viewersPanel, "grow, push");
        return card;
    }

    private void buildRightOnboarding() {
        rightOnboarding.setLayout(new MigLayout("ins 8 8 8 8, gap 20, fill", "[grow,fill]", "[][][grow,fill][]"));
        rightOnboarding.setOpaque(false);

        JLabel title = new JLabel("Share videos from this computer");
        title.setFont(Fonts.ui(800, 24));
        title.setForeground(CrewcastTheme.TEXT);
        JLabel blurb = new JLabel(
                "<html><div style='max-width:460px;line-height:1.55'>Pick what to share, press start, and open the link on any phone, TV or laptop. Nothing is uploaded anywhere.</div>");
        blurb.setFont(Fonts.ui(500, 13));
        blurb.setForeground(new Color(0x9aa4b4));

        rightOnboarding.add(title, "growx, wrap");
        rightOnboarding.add(blurb, "growx, wrap");

        JPanel steps = new JPanel(new MigLayout("ins 0, gap 10, fillx, wrap 1", "[grow,fill]", "[]"));
        steps.setOpaque(false);
        steps.add(onboardingStep("1", "Choose what to share",
                "Folders are rescanned automatically, so new files appear on their own."), "growx, wrap");
        steps.add(onboardingStep("2", "Press Start sharing",
                "A private link, a Wi‑Fi link and a QR code appear here."), "growx, wrap");
        steps.add(onboardingStep("3", "Open the link anywhere",
                "Same Wi‑Fi or across the internet, in any modern browser."), "growx");
        rightOnboarding.add(steps, "grow, push, wrap");

        onboardingStartButton.setVariant(StyledButton.Variant.PRIMARY);
        onboardingStartButton.setEnabled(false);
        onboardingStartButton.setFont(Fonts.ui(800, 15));
        onboardingStartButton.packToContent();
        onboardingStartButton.addActionListener(e -> toggleSharing());

        JLabel hint = new JLabel("Add a folder or file first");
        hint.setFont(Fonts.ui(500, 12.5f));
        hint.setForeground(CrewcastTheme.MUTED);

        JPanel bottom = new JPanel(new MigLayout("ins 0, gap 14", "[][grow,fill]", "[]"));
        bottom.setOpaque(false);
        bottom.add(onboardingStartButton);
        bottom.add(hint, "aligny center");
        rightOnboarding.add(bottom, "growx");
    }

    private RoundedPanel onboardingStep(String n, String title, String body) {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new MigLayout("ins 14 16, gap 14, fillx", "[30!][grow,fill]", "[]"));
        card.setPanelPadding(14, 16, 14, 16);

        JLabel badge = new JLabel(n, JLabel.CENTER);
        badge.setOpaque(true);
        badge.setBackground(CrewcastTheme.ICON_TILE);
        badge.setForeground(CrewcastTheme.LINK);
        badge.setFont(Fonts.ui(800, 13));
        badge.setPreferredSize(new Dimension(30, 30));

        JLabel t = new JLabel(title);
        t.setFont(Fonts.ui(600, 13));
        t.setForeground(CrewcastTheme.TEXT);
        JLabel b = new JLabel(body);
        b.setFont(Fonts.ui(500, 12.5f));
        b.setForeground(CrewcastTheme.MUTED);

        JPanel text = new JPanel(new MigLayout("ins 0, gap 0", "[grow,fill]", "[]2[]"));
        text.setOpaque(false);
        text.add(t, "growx, wrap");
        text.add(b, "growx");

        card.add(badge, "aligny center");
        card.add(text, "growx, wmin 0");
        return card;
    }

    private void buildStatusBarContent() {
        statusBar.setLayout(new BorderLayout());
        statusBar.setBackground(CrewcastTheme.TITLE_BAR);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, CrewcastTheme.BORDER),
                BorderFactory.createEmptyBorder(0, 16, 0, 16)));

        tunnelBarDot.setPreferredSize(new Dimension(7, 7));
        tunnelBarDot.setMinimumSize(new Dimension(7, 7));
        tunnelBarDot.setGlow(false);

        Font barFont = Fonts.ui(500, 12);
        tunnelBarText.setFont(barFont);
        tunnelBarText.setForeground(CrewcastTheme.SECTION_LABEL);
        tunnelBarText.setText("Tunnel off");
        serverPortLabel.setFont(barFont);
        serverPortLabel.setForeground(CrewcastTheme.SECTION_LABEL);
        serverPortLabel.setText("Starting…");
        keepAwakeLabel.setFont(barFont);
        keepAwakeLabel.setForeground(CrewcastTheme.SECTION_LABEL);
        statusBarVersion.setFont(barFont);
        statusBarVersion.setForeground(CrewcastTheme.SECTION_LABEL);

        JPanel left = new JPanel(new MigLayout("ins 0, gap 14, fillx", "[pref!][pref!][grow,fill]", "[30!]"));
        left.setOpaque(false);
        JPanel tunnelSeg = new JPanel(new MigLayout("ins 0, gap 6", "[pref!][pref!]", "[]"));
        tunnelSeg.setOpaque(false);
        tunnelSeg.add(tunnelBarDot, "aligny center, shrink 0");
        tunnelSeg.add(tunnelBarText, "aligny center, shrink 0");
        left.add(tunnelSeg, "shrink 0");
        left.add(serverPortLabel, "aligny center, shrink 0");
        left.add(keepAwakeLabel, "aligny center, growx, wmin 0");

        JPanel east = new JPanel(new MigLayout("ins 0, gap 14", "[]", "[30!]"));
        east.setOpaque(false);
        if (!MAC_OS) {
            east.add(settingsButton, "aligny center, shrink 0");
        }
        east.add(statusBarVersion, "aligny center, shrink 0");

        statusBar.add(left, BorderLayout.WEST);
        statusBar.add(east, BorderLayout.EAST);
    }

    private void toggleSharing() {
        if (sharingService != null && sharingService.isSharingActive()) {
            stopSharing();
        } else {
            startSharing();
        }
    }

    private void startSharing() {
        if (sharingService == null) {
            return;
        }
        sharingService.startSharing();
        refreshDynamicUi();
    }

    private void stopSharing() {
        if (sharingService == null) {
            return;
        }
        sharingService.stopSharing();
        refreshDynamicUi();
    }

    private void regenerateToken() {
        if (sharingService != null) {
            sharingService.regenerateAccessToken();
            refreshDynamicUi();
        }
    }

    private void openSettings() {
        if (sharingService == null) {
            return;
        }
        SettingsDialog dialog = new SettingsDialog(this, sharingService.getSettings(), sharingService);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            sharingService.saveSettings(dialog.result());
            syncFixAudioToggleFromSettings();
        }
    }

    private void syncFixAudioToggleFromSettings() {
        if (sharingService == null) {
            return;
        }
        boolean ffmpeg = sharingService.isFfmpegAvailable();
        fixAudioToggle.setEnabled(ffmpeg);
        fixAudioToggle.setSelected(ffmpeg && sharingService.getSettings().isFixAudioForBrowsers());
    }

    private void reloadShareList() {
        if (sharingService == null) {
            return;
        }
        sharesContainer.removeAll();
        List<ShareListItem> entries = sharingService.listShares();
        for (ShareListItem item : entries) {
            sharesContainer.add(buildShareRow(item), "growx, wmin 0");
        }
        boolean empty = entries.isEmpty();
        CardLayout cards = (CardLayout) listCards.getLayout();
        cards.show(listCards, empty ? "empty" : "list");
        shareHeaderButtons.setVisible(!empty);
        removeAllSharesBtn.setVisible(!empty && entries.size() >= 2);
        compactDropZone.setVisible(!empty);
        playbackSection.setVisible(!empty);
        sharesContainer.revalidate();
        sharesContainer.repaint();
        updateShareHeader();
        updateSharingButtonState();
        updateRightPanelMode();
    }

    private JPanel buildShareRow(ShareListItem item) {
        ShareEntry entry = item.entry();
        RoundedPanel row = new RoundedPanel();
        row.setLayout(new MigLayout("ins 10 10 10 12, gap 10, fillx", "[36!][grow,fill][28!, shrink 0]", "[]"));
        row.setFill(CrewcastTheme.CARD);
        row.setBorderColor(CrewcastTheme.BORDER);
        row.setArc(CrewcastTheme.RADIUS_ROW);

        RoundedPanel iconTile = new RoundedPanel();
        iconTile.setLayout(new MigLayout("ins 0, align center", "[]", "[]"));
        iconTile.setFill(CrewcastTheme.ICON_TILE);
        iconTile.setBorderColor(CrewcastTheme.BORDER);
        iconTile.setArc(9);
        iconTile.setPreferredSize(new Dimension(36, 36));
        String iconName = entry.getKind() == ShareEntry.Kind.FOLDER ? "folder" : "video";
        iconTile.add(new JLabel(Icons.of(iconName, 18, CrewcastTheme.LINK)), "align center");

        ElideLabel name = new ElideLabel();
        name.setFullText(Path.of(entry.getPath()).getFileName().toString());
        name.setFont(Fonts.ui(600, 13));
        name.setForeground(CrewcastTheme.TEXT);
        ElideLabel meta = new ElideLabel();
        String metaText = item.meta();
        if (item.audioMeta() != null && !item.audioMeta().isBlank()) {
            metaText = metaText + " · " + item.audioMeta();
        }
        meta.setFullText(metaText);
        meta.setFont(Fonts.ui(500, 12.5f));
        meta.setForeground(CrewcastTheme.MUTED);
        JPanel text = new JPanel(new MigLayout("ins 0, gap 0", "[grow,fill]", "[]2[]"));
        text.setOpaque(false);
        if (item.hevcWarning()) {
            JPanel nameRow = new JPanel(new MigLayout("ins 0, gap 6", "[][grow,fill]", "[]"));
            nameRow.setOpaque(false);
            JLabel hevcWarn = new JLabel(Icons.of("warning", 14, new java.awt.Color(0xeab308)));
            hevcWarn.setToolTipText(
                    "H.265 video: some devices (older phones, some Android and Windows browsers) may not play the picture.");
            nameRow.add(hevcWarn, "aligny center");
            nameRow.add(name, "growx, wmin 0");
            text.add(nameRow, "growx, wmin 0, wrap");
        } else {
            text.add(name, "growx, wmin 0, wrap");
        }
        text.add(meta, "growx, wmin 0");

        ShareRemoveButton remove = new ShareRemoveButton();
        remove.addActionListener(e -> {
            sharingService.removeShare(item.index());
            reloadShareList();
        });

        row.add(iconTile, "aligny center");
        row.add(text, "growx, wmin 0");
        row.add(remove, "aligny center, shrink 0");
        return row;
    }

    private void updateShareHeader() {
        if (sharingService == null) {
            return;
        }
        List<ShareListItem> items = sharingService.listShares();
        if (items.isEmpty()) {
            shareHeaderLabel.setFullText("Nothing shared yet");
            return;
        }
        int videos = sharingService.countVideos();
        shareHeaderLabel.setFullText(items.size() + (items.size() == 1 ? " item · " : " items · ") + videos
                + (videos == 1 ? " video" : " videos"));
        removeAllSharesBtn.setVisible(items.size() >= 2);
    }

    private void confirmRemoveAllShares() {
        if (sharingService == null || !sharingService.hasShares()) {
            return;
        }
        int n = sharingService.listShares().size();
        int choice = javax.swing.JOptionPane.showConfirmDialog(
                this,
                "Remove all " + n + " items from the share list?\nFiles and folders on disk are not deleted.",
                "Remove all",
                javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
        if (choice == javax.swing.JOptionPane.OK_OPTION) {
            sharingService.removeAllShares();
            reloadShareList();
        }
    }

    private static String formatPortLabel(int port) {
        if (port <= 0) {
            return "Server stopped";
        }
        return "Port " + port;
    }

    private static String formatVersionLabel(String ver) {
        if (ver == null || ver.isBlank()) {
            return "Crewcast v0.0.1";
        }
        if (ver.endsWith("-SNAPSHOT")) {
            ver = ver.substring(0, ver.length() - "-SNAPSHOT".length());
        }
        return "Crewcast v" + ver;
    }

    private void updateSharingButtonState() {
        if (sharingService == null) {
            return;
        }
        boolean hasShares = sharingService.hasShares();
        boolean active = sharingService.isSharingActive();
        if (active) {
            sharingButton.setText("Stop sharing");
            sharingButton.setIcon(Icons.of("stop", 14, CrewcastTheme.DANGER_TEXT));
            sharingButton.setVariant(StyledButton.Variant.DANGER);
            sharingButton.setEnabled(true);
            sharingButton.packToContent();
            sharingButton.setToolTipText(null);
        } else {
            sharingButton.setText("Start sharing");
            sharingButton.setIcon(Icons.of("play", 14, CrewcastTheme.ON_ACCENT));
            sharingButton.setVariant(StyledButton.Variant.PRIMARY);
            sharingButton.setEnabled(hasShares);
            sharingButton.packToContent();
            if (!hasShares) {
                sharingButton.setToolTipText("Add a folder or file to share first");
            } else {
                sharingButton.setToolTipText(null);
            }
        }
        onboardingStartButton.setEnabled(hasShares && !active);
    }

    private void updateRightPanelMode() {
        if (sharingService == null) {
            rightCards.show(rightCardHost, "onboarding");
            return;
        }
        boolean showLive = sharingService.hasShares() || sharingService.isSharingActive();
        rightCards.show(rightCardHost, showLive ? "live" : "onboarding");
        rightCardHost.revalidate();
        rightCardHost.repaint();
    }

    private void refreshDynamicUi() {
        if (sharingService == null || !sharingService.isServerReady()) {
            return;
        }
        updateSharingButtonState();
        updateShareHeader();
        updateRightPanelMode();

        boolean sharing = sharingService.isSharingActive();
        boolean hasShares = sharingService.hasShares();
        statusSharing.setText(sharing ? "Sharing is on" : "Sharing is off");
        statusSharing.setForeground(CrewcastTheme.TEXT);
        statusCardDot.setColor(sharing ? CrewcastTheme.SUCCESS : CrewcastTheme.STATUS_DOT_OFF);
        statusCardDot.setGlow(sharing);
        if (statusCard != null) {
            if (sharing) {
                statusCard.setFill(CrewcastTheme.STATUS_GRADIENT_START);
                statusCard.setGradientEnd(CrewcastTheme.CARD);
                statusCard.setBorderColor(CrewcastTheme.STATUS_BORDER);
            } else {
                statusCard.setFill(CrewcastTheme.CARD);
                statusCard.setGradientEnd(null);
                statusCard.setBorderColor(CrewcastTheme.BORDER);
            }
        }
        if (sharing) {
            boolean tunnelUp = sharingService.getTunnelState() == TunnelState.CONNECTED;
            int viewers = sharingService.activeViewerCount();
            statusViewers.setForeground(CrewcastTheme.SUCCESS_SUBTITLE);
            statusViewers.setText(viewers + (viewers == 1 ? " person watching · " : " people watching · ")
                    + (tunnelUp ? "tunnel connected" : tunnelStateLabel()));
        } else {
            statusViewers.setForeground(CrewcastTheme.MUTED);
            statusViewers.setText(hasShares
                    ? "Press Start sharing when you're ready"
                    : "Add something to share, then start");
        }

        SharingLinks links = sharingService.currentLinks();
        float linkAlpha = sharing ? 1f : 0.55f;
        setLinkCardDimmed(publicLinkCard, linkAlpha);
        setLinkCardDimmed(wifiLinkCard, linkAlpha);
        setLinkCardDimmed(qrCard, linkAlpha);
        qrSegment.setEnabled(sharing);

        if (sharing) {
            String publicLink = links.publicLink();
            if (publicLink.isBlank()) {
                publicLink = "Waiting for tunnel URL…";
            }
            publicLinkField.setActiveUrl(publicLink);
            wifiLinkField.setActiveUrl(links.lanLink());
            setLinkActionsEnabled(true);
            if (newLinkBtn != null) {
                newLinkBtn.setEnabled(true);
            }
        } else {
            publicLinkField.setPlaceholder("Start sharing to get a link");
            wifiLinkField.setPlaceholder("Start sharing to get a link");
            setLinkActionsEnabled(false);
            if (newLinkBtn != null) {
                newLinkBtn.setEnabled(false);
            }
        }

        boolean tunnelUp = sharing && sharingService.getTunnelState() == TunnelState.CONNECTED;
        tunnelBarDot.setColor(tunnelUp ? CrewcastTheme.SUCCESS : CrewcastTheme.STATUS_DOT_OFF);
        tunnelBarText.setText(tunnelUp ? "Tunnel connected" : tunnelBarLabel());
        int port = sharingService.getServerPort();
        serverPortLabel.setText(formatPortLabel(port));
        keepAwakeLabel.setVisible(true);
        keepAwakeLabel.setFullText(sharingService.isKeepAwakeActive() ? "Keeping awake" : "Sleep allowed");

        updateQrImage(sharing);
        refreshViewersList(sharing ? sharingService.activeViewers() : List.of());

        boolean tunnelErr = lastTunnelError != null && !lastTunnelError.isBlank();
        if (tunnelErrRow != null) {
            tunnelErrRow.setVisible(tunnelErr);
        }
        tunnelErrorLabel.setText(tunnelErr ? lastTunnelError : "");
        tunnelRetryButton.setVisible(tunnelErr && lastTunnelRetryable);
    }

    private String tunnelStateLabel() {
        if (sharingService == null) {
            return "idle";
        }
        return switch (sharingService.getTunnelState()) {
            case CONNECTED -> "connected";
            case DOWNLOADING -> "downloading cloudflared";
            case STARTING -> "starting";
            case ERROR -> "error";
            case IDLE -> "idle";
        };
    }

    private String tunnelBarLabel() {
        if (sharingService == null) {
            return "Tunnel off";
        }
        return switch (sharingService.getTunnelState()) {
            case CONNECTED -> "Tunnel connected";
            case DOWNLOADING -> "Downloading tunnel…";
            case STARTING -> "Tunnel starting…";
            case ERROR -> "Tunnel error";
            case IDLE -> sharingService.isSharingActive() ? "Tunnel idle" : "Tunnel off";
        };
    }

    private void setLinkCardDimmed(RoundedPanel card, float alpha) {
        if (card == null) {
            return;
        }
        card.setAlpha(alpha);
    }

    private void setLinkActionsEnabled(boolean enabled) {
        if (publicCopyBtn != null) {
            publicCopyBtn.setEnabled(enabled);
        }
        if (publicOpenBtn != null) {
            publicOpenBtn.setEnabled(enabled);
        }
        if (wifiCopyBtn != null) {
            wifiCopyBtn.setEnabled(enabled);
        }
        if (wifiOpenBtn != null) {
            wifiOpenBtn.setEnabled(enabled);
        }
    }

    private void refreshViewersList(List<ViewerView> viewers) {
        viewersPanel.removeAll();
        if (viewers.isEmpty()) {
            JLabel none = new JLabel("No one watching yet");
            none.setFont(Fonts.ui(500, 12.5f));
            none.setForeground(CrewcastTheme.MUTED);
            viewersPanel.add(none, "growx");
        } else {
            for (ViewerView v : viewers) {
                viewersPanel.add(viewerRow(v), "growx");
            }
        }
        viewersPanel.revalidate();
        viewersPanel.repaint();
    }

    private JPanel viewerRow(ViewerView v) {
        JPanel row = new JPanel(new MigLayout("ins 8 10, gap 10, fillx", "[8!][grow,fill][pref!]", "[]"));
        row.setOpaque(true);
        row.setBackground(CrewcastTheme.VIEWER_ROW);

        StatusDot dot = new StatusDot();
        dot.setPreferredSize(new Dimension(8, 8));
        dot.setMinimumSize(new Dimension(8, 8));
        dot.setGlow(false);
        dot.setColor(CrewcastTheme.SUCCESS);

        ElideLabel device = new ElideLabel();
        device.setFullText(v.device());
        device.setFont(Fonts.ui(600, 12));
        device.setForeground(CrewcastTheme.TEXT);
        String via = v.viaTunnel() ? "via tunnel" : "on Wi‑Fi";
        ElideLabel sub = new ElideLabel();
        sub.setFullText(v.fileName() + " · " + via);
        sub.setFont(Fonts.ui(500, 12));
        sub.setForeground(CrewcastTheme.MUTED);
        JPanel text = new JPanel(new MigLayout("ins 0, gap 0", "[grow,fill]", "[]1[]"));
        text.setOpaque(false);
        text.add(device, "growx, wmin 0, wrap");
        text.add(sub, "growx, wmin 0");

        StyledButton remove = new StyledButton("Remove");
        remove.setVariant(StyledButton.Variant.GHOST);
        remove.setForeground(CrewcastTheme.DANGER_TEXT);
        remove.setFont(Fonts.ui(600, 11));
        remove.addActionListener(e -> {
            sharingService.removeViewer(v.key());
            refreshDynamicUi();
        });

        row.add(dot, "aligny center");
        row.add(text, "growx, wmin 0");
        row.add(remove, "aligny center");
        return row;
    }

    private void updateQrImage() {
        updateQrImage(sharingService != null && sharingService.isSharingActive());
    }

    private void updateQrImage(boolean sharingActive) {
        if (qrCodeTile == null) {
            return;
        }
        if (!sharingActive || sharingService == null) {
            qrCodeTile.showPlaceholder();
            return;
        }
        SharingLinks links = sharingService.currentLinks();
        String url = qrUrlForEncoding(links);
        if (url == null || url.isBlank()) {
            qrCodeTile.showPlaceholder();
            return;
        }
        qrCodeTile.setQrUrl(url);
    }

    private String qrUrlForEncoding(SharingLinks links) {
        if (qrShowsPublic) {
            if (links.publicLink() != null && !links.publicLink().isBlank()) {
                return links.publicLink();
            }
            return links.lanLink();
        }
        if (links.lanLink() != null && !links.lanLink().isBlank()) {
            return links.lanLink();
        }
        return links.publicLink();
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            addSharePath(chooser.getSelectedFile().toPath(), ShareEntry.Kind.FOLDER);
        }
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("Video files", "mp4", "mkv", "mov", "webm", "m4v"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) {
                addSharePath(f.toPath(), ShareEntry.Kind.FILE);
            }
        }
    }

    private void addSharePath(Path path, ShareEntry.Kind kind) {
        if (sharingService == null) {
            return;
        }
        try {
            if (kind == ShareEntry.Kind.FOLDER) {
                sharingService.addFolder(path);
            } else {
                sharingService.addFile(path);
            }
            reloadShareList();
        } catch (IOException ex) {
            javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage(), "Could not add share",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private void installDropTarget(Component c) {
        new DropTarget(c, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) dtde.getTransferable()
                            .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    for (File f : files) {
                        if (f.isDirectory()) {
                            addSharePath(f.toPath(), ShareEntry.Kind.FOLDER);
                        } else if (sharingService != null && sharingService.isSupportedVideoFileName(f.getName())) {
                            addSharePath(f.toPath(), ShareEntry.Kind.FILE);
                        }
                    }
                    dtde.dropComplete(true);
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                }
            }
        });
    }

    private static void copyText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
    }

    private static void openUrl(String url) {
        if (url == null || url.isBlank() || url.startsWith("Waiting")) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onTunnelDownloadProgress(int percent, String message) {
        SwingUtilities.invokeLater(() -> {
            downloadBanner.setVisible(true);
            downloadTitle.setText(message != null && !message.isBlank() ? message : "Setting up the secure tunnel");
            downloadSubtitle.setText("Downloading cloudflared once, so public links work. This only happens on first launch.");
            downloadProgress.setPercent(Math.max(0, Math.min(100, percent)));
            downloadPercent.setText(percent + "%");
        });
    }

    @Override
    public void onTunnelUrl(String url) {
        SwingUtilities.invokeLater(() -> {
            downloadBanner.setVisible(false);
            lastTunnelError = null;
            refreshDynamicUi();
        });
    }

    @Override
    public void onTunnelStateChanged(TunnelState state, String detail) {
        SwingUtilities.invokeLater(this::refreshDynamicUi);
    }

    @Override
    public void onTunnelError(String message, boolean retryable) {
        SwingUtilities.invokeLater(() -> {
            lastTunnelError = message;
            lastTunnelRetryable = retryable;
            refreshDynamicUi();
        });
    }

    @Override
    public void onViewersChanged(List<ViewerView> viewers) {
        SwingUtilities.invokeLater(() -> refreshViewersList(viewers));
    }

    /** Placeholder for mockup title-bar balance (80px). */
    private static final class BoxPlaceholder {
        static Component spacer(int width) {
            return javax.swing.Box.createHorizontalStrut(width);
        }
    }

    /** Large dashed empty-state surface (Idle mockup). */
    private static final class EmptyDropSurface extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(CrewcastTheme.DASHED);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6, 6}, 0));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** 220×6 download progress bar matching banner mockup. */
    private static final class DownloadProgressBar extends JPanel {
        private int percent;

        DownloadProgressBar() {
            setOpaque(false);
            setPreferredSize(new Dimension(220, 6));
            setMinimumSize(new Dimension(220, 6));
        }

        void setPercent(int percent) {
            this.percent = percent;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(CrewcastTheme.BANNER_BORDER);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 3, 3);
            int fillW = Math.max(0, (int) Math.round(getWidth() * (percent / 100.0)));
            if (fillW > 0) {
                g2.setColor(CrewcastTheme.ACCENT);
                g2.fillRoundRect(0, 0, fillW, getHeight(), 3, 3);
            }
            g2.dispose();
        }
    }
}
