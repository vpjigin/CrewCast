package com.solocrew.crewcast.desktop.share;

import com.solocrew.crewcast.facade.ConversionQueueSummary;
import com.solocrew.crewcast.facade.ShareListItem;
import com.solocrew.crewcast.facade.ShareVideoCompatRow;
import com.solocrew.crewcast.media.CompatUiState;
import com.solocrew.crewcast.settings.ShareEntry;
import com.solocrew.crewcast.share.ShareRegistry;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/** Cached share-list row model (compat fields) without any window. */
public final class ShareListController {

    private List<ShareListItem> items = List.of();
    private ShareListUiMode mode = ShareListUiMode.NORMAL;
    private boolean lastApplyUsedChecking;
    private ConversionQueueSummary queueSummaryOverride;

    public List<ShareListItem> items() {
        return items;
    }

    public ShareListUiMode mode() {
        return mode;
    }

    public boolean lastApplyUsedChecking() {
        return lastApplyUsedChecking;
    }

    public void setQueueSummaryOverride(ConversionQueueSummary summary) {
        queueSummaryOverride = summary;
    }

    public void apply(List<ShareListItem> entries, ShareListUiMode uiMode) {
        mode = uiMode;
        lastApplyUsedChecking = uiMode == ShareListUiMode.CHECKING;
        items = List.copyOf(entries);
    }

    public CompatUiState firstItemCompatState() {
        return items.isEmpty() ? null : items.get(0).fileCompatState();
    }

    public int firstItemCompatProgress() {
        return items.isEmpty() ? -1 : items.get(0).fileCompatProgress();
    }

    public Integer firstItemCompatEtaSeconds() {
        return items.isEmpty() ? null : items.get(0).fileCompatEtaSeconds();
    }

    public Double firstItemCompatSpeed() {
        return items.isEmpty() ? null : items.get(0).fileCompatSpeed();
    }

    /** Test / offline equivalent of convert-now without touching {@code MainFrame}. */
    public void simulateConvertNow(ShareListItem targetShare) {
        applyOptimisticCompatStart(targetShare, null, () -> {
            throw new IllegalStateException("no sharing service");
        });
    }

    /** Same optimistic patch as convert-now when the server refresh is unavailable. */
    public void applyOptimisticCompatStart(
            ShareListItem targetShare, Path folderVideoPath, Supplier<ConversionQueueSummary> queueFromService) {
        if (items.isEmpty()) {
            return;
        }
        CompatUiState state = optimisticCompatStateAfterEnqueue(queueFromService);
        List<ShareListItem> patched = items.stream()
                .map(item -> patchShareItemAfterCompatStart(item, targetShare, folderVideoPath, state))
                .toList();
        apply(patched, ShareListUiMode.NORMAL);
    }

    private CompatUiState optimisticCompatStateAfterEnqueue(Supplier<ConversionQueueSummary> queueFromService) {
        try {
            ConversionQueueSummary q = queueSummaryOverride != null ? queueSummaryOverride : queueFromService.get();
            if (q.running() > 0) {
                return CompatUiState.CONVERTING;
            }
            if (q.queued() > 0) {
                return CompatUiState.QUEUED;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return CompatUiState.CONVERTING;
    }

    private static ShareListItem patchShareItemAfterCompatStart(
            ShareListItem item,
            ShareListItem targetShare,
            Path folderVideoPath,
            CompatUiState state) {
        if (targetShare != null && item.index() == targetShare.index()) {
            if (folderVideoPath == null
                    && item.entry().getKind() == ShareEntry.Kind.FILE
                    && ShareRegistry.isVideoFileName(Path.of(item.entry().getPath()).getFileName().toString())) {
                return withCompatFields(item, state, state == CompatUiState.CONVERTING ? 0 : 0, 0);
            }
            if (folderVideoPath != null && item.folderVideos() != null && !item.folderVideos().isEmpty()) {
                List<ShareVideoCompatRow> videos = item.folderVideos().stream()
                        .map(v -> v.path().equals(folderVideoPath)
                                ? withFolderVideoCompat(v, state, state == CompatUiState.CONVERTING ? 0 : 0, 0)
                                : v)
                        .toList();
                return new ShareListItem(
                        item.index(), item.entry(), item.meta(), item.audioMeta(), item.subtitleMeta(),
                        item.hevcWarning(), item.compatSummary(), item.fileCompatState(), item.fileCompatProgress(),
                        item.fileCompatTooltip(), item.fileCompatCanPrepare(), item.fileCompatCanCancel(),
                        item.fileCompatQueueAhead(), item.fileCompatSpeed(), item.fileCompatEtaSeconds(),
                        item.fileCompatStartedBy(), item.fileCompatStartedByLabel(), videos);
            }
        }
        return item;
    }

    private static ShareListItem withCompatFields(
            ShareListItem item, CompatUiState state, int progress, int queueAhead) {
        return new ShareListItem(
                item.index(),
                item.entry(),
                item.meta(),
                item.audioMeta(),
                item.subtitleMeta(),
                item.hevcWarning(),
                item.compatSummary(),
                state,
                progress,
                item.fileCompatTooltip(),
                false,
                state == CompatUiState.CONVERTING || state == CompatUiState.QUEUED,
                queueAhead,
                state == CompatUiState.CONVERTING ? item.fileCompatSpeed() : null,
                state == CompatUiState.CONVERTING ? item.fileCompatEtaSeconds() : null,
                item.fileCompatStartedBy(),
                item.fileCompatStartedByLabel(),
                item.folderVideos());
    }

    private static ShareVideoCompatRow withFolderVideoCompat(
            ShareVideoCompatRow v, CompatUiState state, int progress, int queueAhead) {
        return new ShareVideoCompatRow(
                v.path(),
                v.displayName(),
                state,
                progress,
                v.tooltip(),
                false,
                state == CompatUiState.CONVERTING || state == CompatUiState.QUEUED,
                queueAhead,
                v.speed(),
                v.etaSeconds(),
                v.startedBy(),
                v.startedByLabel());
    }
}
