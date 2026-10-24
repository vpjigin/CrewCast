package com.solocrew.crewcast.desktop.share;

import com.solocrew.crewcast.facade.ShareListItem;
import com.solocrew.crewcast.facade.ShareVideoCompatRow;
import com.solocrew.crewcast.settings.ShareEntry;

import java.awt.Component;
import java.nio.file.Path;

public final class ShareRowTestSupport {

    private ShareRowTestSupport() {}

    public static ShareRowFactory layoutTestFactory() {
        ShareRowContext ctx = new ShareRowContext(() -> true, path -> 12, noopActions());
        return new ShareRowFactory(ctx);
    }

    static ShareRowActions noopActions() {
        return new ShareRowActions() {
            @Override
            public void removeShare(int index) {}

            @Override
            public void reloadShareList(boolean showCheckingPlaceholder) {}

            @Override
            public void showCompatPopup(Component invoker, ShareListItem shareItem, ShareVideoCompatRow videoRow) {}

            @Override
            public void prepareCompatForShareEntry(ShareEntry entry, ShareListItem shareItem, Path folderVideoPath) {}

            @Override
            public void cancelCompatForPath(Path path, ShareListItem shareItem) {}

            @Override
            public void prepareAllNeedingCompat() {}

            @Override
            public void prepareAllCompatForFolder(ShareEntry folderEntry) {}
        };
    }
}
