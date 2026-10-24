package com.solocrew.crewcast.desktop.share;

import com.solocrew.crewcast.desktop.theme.CrewcastTheme;
import com.solocrew.crewcast.desktop.ui.ElideLabel;
import com.solocrew.crewcast.desktop.ui.Fonts;
import com.solocrew.crewcast.desktop.ui.Icons;
import com.solocrew.crewcast.desktop.ui.LoadingBar;
import com.solocrew.crewcast.desktop.ui.PlainTextLabels;
import com.solocrew.crewcast.desktop.ui.RoundedPanel;
import com.solocrew.crewcast.desktop.ui.ShareRemoveButton;
import com.solocrew.crewcast.desktop.ui.StyledButton;
import com.solocrew.crewcast.desktop.ui.WrapLabel;
import com.solocrew.crewcast.facade.ShareListItem;
import com.solocrew.crewcast.facade.ShareVideoCompatRow;
import com.solocrew.crewcast.media.CompatFixService;
import com.solocrew.crewcast.media.CompatUiState;
import com.solocrew.crewcast.media.ConversionStartedBy;
import com.solocrew.crewcast.settings.ShareEntry;
import com.solocrew.crewcast.share.ShareRegistry;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Builds share list rows (JPanel only — no frame or dialog). */
public final class ShareRowFactory {

    private static final Logger LOG = Logger.getLogger(ShareRowFactory.class.getName());

    private final ShareRowContext context;

    public ShareRowFactory(ShareRowContext context) {
        this.context = context;
    }

    public JPanel buildShareRowFallback(ShareListItem item) {
        RoundedPanel row = new RoundedPanel();
        row.setLayout(new MigLayout("ins 12, fillx", "[grow,fill]", "[]"));
        row.setFill(CrewcastTheme.CARD);
        row.setBorderColor(CrewcastTheme.BORDER);
        row.setArc(CrewcastTheme.RADIUS_ROW);
        String name = Path.of(item.entry().getPath()).getFileName().toString();
        WrapLabel line = new WrapLabel();
        line.setFont(Fonts.ui(500, 12.5f));
        line.setForeground(CrewcastTheme.MUTED);
        line.setFullText("Couldn't display " + name);
        row.add(line, "growx, wmin 0");
        return row;
    }

    public JPanel buildShareRow(ShareListItem item, ShareListUiMode mode) {
        JPanel block = new JPanel(new MigLayout("ins 0, gap 4, fillx", "[grow,fill]", "[]"));
        block.setOpaque(false);
        block.add(buildShareRowMain(item, mode), "growx, wrap");
        if (mode == ShareListUiMode.NORMAL
                && item.entry().getKind() == ShareEntry.Kind.FOLDER
                && item.folderVideos() != null
                && !item.folderVideos().isEmpty()) {
            JPanel nested = new JPanel(new MigLayout("ins 0, gap 4, fillx", "[grow,fill]", "[]"));
            nested.setOpaque(false);
            for (ShareVideoCompatRow v : item.folderVideos()) {
                try {
                    nested.add(buildFolderVideoCompatRow(v, item), "growx, wrap");
                } catch (Throwable ex) {
                    LOG.log(Level.SEVERE, "Failed to build folder video row for " + v.path(), ex);
                    nested.add(buildShareRowFallback(new ShareListItem(
                            item.index(),
                            new ShareEntry(v.path().toString(), ShareEntry.Kind.FILE),
                            "", "", "", false, "",
                            CompatUiState.NOT_NEEDED, 0, "", false, false, 0,
                            null, null, null, null, List.of())), "growx, wrap");
                }
            }
            block.add(nested, "growx");
        }
        return block;
    }

    public JPanel buildShareRowMain(ShareListItem item, ShareListUiMode mode) {
        ShareEntry entry = item.entry();
        RoundedPanel row = new RoundedPanel();
        row.setLayout(new MigLayout("ins 12, gap 8, fillx", "[grow,fill]", "[][][]"));
        row.setFill(CrewcastTheme.CARD);
        row.setBorderColor(CrewcastTheme.BORDER);
        row.setArc(CrewcastTheme.RADIUS_ROW);

        row.add(buildShareTopLine(item), "growx, wrap");
        boolean videoFile = entry.getKind() == ShareEntry.Kind.FILE
                && ShareRegistry.isVideoFileName(Path.of(entry.getPath()).getFileName().toString());
        if (mode == ShareListUiMode.CHECKING && videoFile) {
            row.add(wrapPlainStatus("Checking…", CrewcastTheme.MUTED), "growx, wmin 0, wrap");
        } else if (mode == ShareListUiMode.CHECK_FAILED && videoFile) {
            row.add(buildShareProbeFailedLine(item), "growx, wmin 0, wrap");
        } else if (videoFile) {
            CompatRowModel model = compatRowFromShareItem(item);
            JPanel status = buildCompatStatusSection(model);
            if (status != null) {
                row.add(status, "growx, wmin 0, wrap");
                JPanel action = buildCompatActionSection(model, item, null);
                if (action != null) {
                    row.add(action, "growx, wrap");
                }
            }
        }
        return row;
    }

    private JPanel buildShareTopLine(ShareListItem item) {
        ShareEntry entry = item.entry();
        JPanel top = new JPanel(new MigLayout("ins 0, gap 10, fillx", "[36!][grow,fill][pref!]", "[]"));
        top.setOpaque(false);

        RoundedPanel iconTile = new RoundedPanel();
        iconTile.setLayout(new MigLayout("ins 0, align center", "[]", "[]"));
        iconTile.setFill(CrewcastTheme.ICON_TILE);
        iconTile.setBorderColor(CrewcastTheme.BORDER);
        iconTile.setArc(9);
        iconTile.setPreferredSize(new Dimension(36, 36));
        String iconName = entry.getKind() == ShareEntry.Kind.FOLDER ? "folder" : "video";
        iconTile.add(new JLabel(Icons.of(iconName, 18, CrewcastTheme.LINK)), "align center");
        if (item.subtitleMeta() != null && !item.subtitleMeta().isBlank()) {
            iconTile.setToolTipText(item.subtitleMeta());
        }

        ElideLabel name = new ElideLabel();
        name.setFullText(Path.of(entry.getPath()).getFileName().toString());
        name.setFont(Fonts.ui(600, 13));
        name.setForeground(CrewcastTheme.TEXT);
        WrapLabel meta = new WrapLabel();
        meta.setFullText(item.meta() != null ? item.meta() : "");
        meta.setFont(Fonts.ui(500, 12.5f));
        meta.setForeground(CrewcastTheme.MUTED);
        JPanel text = new JPanel(new MigLayout("ins 0, gap 0", "[grow,fill]", "[]2[]"));
        text.setOpaque(false);
        text.add(name, "growx, wmin 0, wrap");
        text.add(meta, "growx, wmin 0");

        JPanel controls = new JPanel(new MigLayout("ins 0, gap 4", "[]", "[]"));
        controls.setOpaque(false);
        if (entry.getKind() == ShareEntry.Kind.FILE || entry.getKind() == ShareEntry.Kind.FOLDER) {
            StyledButton tvMenu = new StyledButton("TV");
            tvMenu.setVariant(StyledButton.Variant.GHOST);
            tvMenu.packToContent();
            tvMenu.setToolTipText("Prepare or cancel TV-safe H.264 version");
            tvMenu.addActionListener(e -> context.actions().showCompatPopup(tvMenu, item, null));
            controls.add(tvMenu);
        }
        ShareRemoveButton remove = new ShareRemoveButton();
        remove.addActionListener(e -> {
            context.actions().removeShare(item.index());
            context.actions().reloadShareList(true);
        });
        controls.add(remove);

        top.add(iconTile, "aligny top");
        top.add(text, "growx, wmin 0");
        top.add(controls, "aligny top");
        return top;
    }

    private JPanel buildFolderVideoCompatRow(ShareVideoCompatRow v, ShareListItem folderItem) {
        RoundedPanel row = new RoundedPanel();
        row.setLayout(new MigLayout("ins 12, gap 8, fillx", "[grow,fill]", "[][][]"));
        row.setFill(CrewcastTheme.CARD);
        row.setBorderColor(CrewcastTheme.BORDER);
        row.setArc(CrewcastTheme.RADIUS_ROW);

        JPanel top = new JPanel(new MigLayout("ins 0, gap 10, fillx", "[grow,fill][pref!]", "[]"));
        top.setOpaque(false);
        ElideLabel name = new ElideLabel();
        name.setFullText(v.displayName());
        name.setFont(Fonts.ui(600, 13));
        name.setForeground(CrewcastTheme.TEXT);
        StyledButton tvMenu = new StyledButton("TV");
        tvMenu.setVariant(StyledButton.Variant.GHOST);
        tvMenu.packToContent();
        tvMenu.addActionListener(e -> context.actions().showCompatPopup(tvMenu, folderItem, v));
        top.add(name, "growx, wmin 0");
        top.add(tvMenu, "aligny top");
        row.add(top, "growx, wrap");

        CompatRowModel model = compatRowFromFolderVideo(v);
        JPanel status = buildCompatStatusSection(model);
        if (status != null) {
            row.add(status, "growx, wmin 0, wrap");
            JPanel action = buildCompatActionSection(model, folderItem, v);
            if (action != null) {
                row.add(action, "growx, wrap");
            }
        }
        return row;
    }

    private CompatRowModel compatRowFromShareItem(ShareListItem item) {
        return new CompatRowModel(
                item.fileCompatState(),
                item.fileCompatProgress(),
                item.fileCompatTooltip(),
                item.fileCompatQueueAhead(),
                item.fileCompatSpeed(),
                item.fileCompatEtaSeconds(),
                item.fileCompatStartedBy(),
                item.fileCompatStartedByLabel(),
                Path.of(item.entry().getPath()));
    }

    private CompatRowModel compatRowFromFolderVideo(ShareVideoCompatRow v) {
        return new CompatRowModel(
                v.state(),
                v.progress(),
                v.tooltip(),
                v.queueAhead(),
                v.speed(),
                v.etaSeconds(),
                v.startedBy(),
                v.startedByLabel(),
                v.path());
    }

    private JPanel buildCompatStatusSection(CompatRowModel model) {
        if (model.state() == CompatUiState.NOT_NEEDED) {
            return null;
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Share row compat render: path="
                    + model.videoPath()
                    + " uiState="
                    + model.state()
                    + " progress="
                    + model.progress()
                    + "% eta="
                    + model.etaSeconds()
                    + " speed="
                    + model.speed());
        }
        JPanel wrap = new JPanel(new MigLayout("ins 0, fillx", "[grow,fill]", "[]"));
        wrap.setOpaque(false);
        switch (model.state()) {
            case NEEDED -> {
                if (!Boolean.TRUE.equals(context.encoderAvailable().get())) {
                    wrap.add(wrapPlainStatus(model.tooltip(), CrewcastTheme.DANGER_TEXT), "growx");
                    return wrap;
                }
                wrap.add(amberCompatCallout(
                        "Won't play in most browsers, phones or TVs until converted"), "growx");
                return wrap;
            }
            case FAILED -> {
                String msg = model.tooltip() != null && !model.tooltip().isBlank()
                        ? model.tooltip()
                        : "TV version: conversion failed";
                wrap.add(wrapPlainStatus(msg, CrewcastTheme.DANGER_TEXT), "growx");
                return wrap;
            }
            case UNAVAILABLE -> {
                wrap.add(wrapPlainStatus(
                        model.tooltip() != null ? model.tooltip() : CompatFixService.NO_ENCODER_MESSAGE,
                        CrewcastTheme.DANGER_TEXT), "growx");
                return wrap;
            }
            case QUEUED -> {
                int ahead = model.queueAhead();
                String q = ahead > 0 ? "Queued – " + ahead + " ahead" : "Starting…";
                wrap.add(wrapPlainStatus(q, CrewcastTheme.MUTED), "growx");
                return wrap;
            }
            case CONVERTING -> {
                JPanel col = new JPanel(new MigLayout("ins 0, gap 6, fillx", "[grow,fill]", "[][][]"));
                col.setOpaque(false);
                boolean finishing = model.tooltip() != null && model.tooltip().startsWith("Finishing");
                LoadingBar bar = new LoadingBar();
                bar.setPercent(finishing ? 99 : model.progress());
                col.add(bar, "growx, wrap");
                JPanel stats = new JPanel(new MigLayout("ins 0, fillx", "[grow,fill][pref!]", "[]"));
                stats.setOpaque(false);
                WrapLabel left = new WrapLabel();
                left.setFont(Fonts.ui(500, 12f));
                left.setForeground(CrewcastTheme.TEXT);
                left.setFullText(finishing ? "Finishing up…" : "Converting · " + model.progress() + "%");
                WrapLabel right = new WrapLabel();
                right.setFont(Fonts.ui(500, 12f));
                right.setForeground(CrewcastTheme.MUTED);
                String statsText = formatConvertingStats(model.etaSeconds(), model.speed());
                right.setFullText(statsText);
                right.setVisible(!finishing && !statsText.isBlank());
                stats.add(left, "growx, wmin 0");
                stats.add(right, "alignx right");
                col.add(stats, "growx, wrap");
                if (model.startedBy() == ConversionStartedBy.VIEWER && model.startedByLabel() != null) {
                    WrapLabel from = new WrapLabel();
                    from.setFont(Fonts.ui(500, 11f));
                    from.setForeground(CrewcastTheme.MUTED);
                    from.setFullText("Started from " + PlainTextLabels.forUi(model.startedByLabel()));
                    col.add(from, "growx, wmin 0");
                }
                wrap.add(col, "growx");
                return wrap;
            }
            case READY -> {
                JPanel ready = new JPanel(new MigLayout("ins 0, gap 6", "[pref!][grow,fill]", "[]"));
                ready.setOpaque(false);
                JLabel check = new JLabel("✓");
                check.setFont(Fonts.ui(700, 14));
                check.setForeground(CrewcastTheme.SUCCESS);
                ready.add(check, "aligny top");
                WrapLabel line = new WrapLabel();
                line.setFont(Fonts.ui(500, 12f));
                line.setForeground(CrewcastTheme.SUCCESS);
                line.setFullText("Ready everywhere");
                ready.add(line, "growx, wmin 0");
                wrap.add(ready, "growx");
                return wrap;
            }
            default -> {
                return null;
            }
        }
    }

    private static String formatConvertingStats(Integer etaSeconds, Double speed) {
        String eta = etaSeconds != null && etaSeconds > 0
                ? Math.max(1, etaSeconds / 60) + " min left"
                : "";
        String spd = speed != null
                ? String.format(Locale.US, "%.1f× speed", speed)
                : "";
        if (eta.isEmpty()) {
            return spd;
        }
        if (spd.isEmpty()) {
            return eta;
        }
        return eta + " · " + spd;
    }

    private JPanel amberCompatCallout(String message) {
        RoundedPanel box = new RoundedPanel();
        box.setLayout(new MigLayout("ins 8 10, gap 8, fillx", "[pref!][grow,fill]", "[]"));
        box.setFill(new Color(0x2a2410));
        box.setBorderColor(new Color(0x5c4a18));
        box.setArc(8);
        box.add(new JLabel(Icons.of("warning", 16, new Color(0xeab308))), "aligny top");
        WrapLabel line = new WrapLabel();
        line.setFont(Fonts.ui(500, 12f));
        line.setForeground(new Color(0xeab308));
        line.setFullText(message);
        box.add(line, "growx, wmin 0");
        return box;
    }

    private JPanel wrapPlainStatus(String message, Color color) {
        WrapLabel line = new WrapLabel();
        line.setFont(Fonts.ui(500, 12f));
        line.setForeground(color);
        line.setFullText(message);
        JPanel p = new JPanel(new MigLayout("ins 0, fillx", "[grow,fill]", "[]"));
        p.setOpaque(false);
        p.add(line, "growx, wmin 0");
        return p;
    }

    private JPanel buildCompatActionSection(CompatRowModel model, ShareListItem shareItem, ShareVideoCompatRow videoRow) {
        return switch (model.state()) {
            case NEEDED, FAILED -> {
                if (!Boolean.TRUE.equals(context.encoderAvailable().get())) {
                    yield null;
                }
                JPanel row = new JPanel(new MigLayout("ins 0, gap 10, fillx", "[pref!][grow,fill]", "[]"));
                row.setOpaque(false);
                StyledButton convertBtn = new StyledButton("Convert now");
                convertBtn.setVariant(StyledButton.Variant.SECONDARY);
                convertBtn.packToContent();
                convertBtn.addActionListener(e -> {
                    if (videoRow != null) {
                        context.actions().prepareCompatForShareEntry(
                                new ShareEntry(videoRow.path().toString(), ShareEntry.Kind.FILE),
                                shareItem,
                                videoRow.path());
                    } else if (shareItem != null) {
                        context.actions().prepareCompatForShareEntry(shareItem.entry(), shareItem, null);
                    }
                });
                WrapLabel hint = new WrapLabel();
                hint.setFont(Fonts.ui(500, 11.5f));
                hint.setForeground(CrewcastTheme.MUTED);
                hint.setFullText(compatPrepareHint(model.videoPath()));
                row.add(convertBtn, "aligny center");
                row.add(hint, "growx, wmin 0, aligny center");
                yield row;
            }
            case CONVERTING -> {
                JPanel row = new JPanel(new MigLayout("ins 0, fillx", "[pref!]", "[]"));
                row.setOpaque(false);
                StyledButton cancel = new StyledButton("Cancel");
                cancel.setVariant(StyledButton.Variant.GHOST);
                cancel.packToContent();
                cancel.addActionListener(e -> {
                    Path path = videoRow != null ? videoRow.path()
                            : shareItem != null ? Path.of(shareItem.entry().getPath()) : null;
                    if (path != null) {
                        context.actions().cancelCompatForPath(path, shareItem);
                    }
                });
                row.add(cancel, "aligny center");
                yield row;
            }
            default -> null;
        };
    }

    private String compatPrepareHint(Path videoPath) {
        Integer est = context.estimateCompatMinutes().apply(videoPath);
        if (est != null) {
            return "About " + est + " min · uses this laptop's GPU";
        }
        return "Uses this laptop's GPU";
    }

    private JPanel buildShareProbeFailedLine(ShareListItem item) {
        JPanel row = new JPanel(new MigLayout("ins 0, gap 8, fillx", "[grow,fill][pref!]", "[]"));
        row.setOpaque(false);
        WrapLabel line = new WrapLabel();
        line.setFont(Fonts.ui(500, 12f));
        line.setForeground(CrewcastTheme.DANGER_TEXT);
        line.setFullText("Couldn't check this file");
        StyledButton retry = new StyledButton("Retry");
        retry.setVariant(StyledButton.Variant.GHOST);
        retry.packToContent();
        retry.addActionListener(e -> context.actions().reloadShareList(false));
        row.add(line, "growx, wmin 0");
        row.add(retry, "aligny center");
        return row;
    }
}
